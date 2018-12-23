package com.medleystudios.pn.conn;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.packets.PNPacket;
import com.medleystudios.pn.conn.packets.PNPackets;
import com.medleystudios.pn.conn.packets.PNPacketReceiver;
import com.medleystudios.pn.io.PNAsyncBufferedInputStream;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.io.PNPacketInputStream;
import com.medleystudios.pn.io.PNPacketOutputStream;
import com.medleystudios.pn.util.error.PNError;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class PNConnection implements PNErrorStorable {

   private final Object closeLock = new Object();

   private final ConnectionId id;
   private boolean disconnectedSelf = false;
   private boolean closed = false;
   private Socket socket;
   private PNPacketInputStream in;
   private PNPacketOutputStream out;
   private PNInputStreamReader reader;
   private PNOutputStreamWriter writer;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   private PNPacketReceiver packetReceiver = null;

   public static CompletableFuture<ServerSocketResolver> host(String host, int port) {
      return supplyAsync(() -> {
         ServerSocket serverSocket;
         try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(host, port));
            return new ServerSocketResolver(serverSocket);
         }
         catch (IOException e) {
            PN.error(e, "Failed to host server " + host + ":" + port);
            return new ServerSocketResolver(e.getMessage());
         }
      });
   }

   public static CompletableFuture<SocketResolver> connect(String host, int port) {
      return supplyAsync(() -> {
         Socket clientSocket;
         try {
            clientSocket = new Socket();
            clientSocket.setReuseAddress(true);
            clientSocket.setTcpNoDelay(true);
            clientSocket.connect(new InetSocketAddress(host, port), 0);
            return new SocketResolver(clientSocket);
         }
         catch (IOException e) {
            PN.error(e, "Failed to connect to " + host + ":" + port);
            return new SocketResolver(e.getMessage());
         }
      });
   }

   public static CompletableFuture<PNConnection> get(Socket socket) {
      return supplyAsync(() -> new PNConnection(socket));
   }

   private PNConnection(Socket socket) {
      this.id = ConnectionId.next();
      this.socket = socket;

      if (this.socket.isClosed()) {
         throw new RuntimeException("Socket is already closed and cannot be used " + this.socket);
      }

      if (!this.socket.isConnected()) {
         throw new RuntimeException("Socket is not connected and cannot be used " + this.socket);
      }

      this.startIO();
   }

   private synchronized void startIO() {
      InputStream in = null;
      OutputStream out = null;

      try {
         in = this.socket.getInputStream();
      }
      catch (IOException e) {
         PN.fatalError(e, this, "Failed to get socket input stream " + this.socket);
      }

      try {
         out = this.socket.getOutputStream();
      }
      catch (IOException e) {
         PN.fatalError(e, this, "Failed to get socket output stream " + this.socket);
      }

      this.in = PNPacketInputStream.start(in, this::onInputStreamClosed);
      this.out = PNPacketOutputStream.start(out, this::onOutputStreamClosed);
      this.getErrorStorage()
         .addChild(this.in.getErrorStorage())
         .addChild(this.out.getErrorStorage());
      this.reader = new PNInputStreamReader(this.in);
      this.writer = new PNOutputStreamWriter(this.out);
   }

   public synchronized void setPacketReceiver(PNPacketReceiver packetReceiver) {
      this.packetReceiver = packetReceiver;
   }

   public synchronized void process() {
      try {
         processImpl();
      }
      catch (IndexOutOfBoundsException e) {
         PN.storeAndLogError(e, this, "Failed to process data due to malformed data! Closing connection.");
         close();
      }
      catch (IOException e) {
         PN.storeAndLogError(e, this, "Failed to process data due to IOException! Closing connection.");
         close();
      }
   }

   protected void processImpl() throws IOException, IndexOutOfBoundsException {
      synchronized (in.getBufferLock()) {
         if (this.packetReceiver == null) return;
         while (readPacket(getReader()));
      }
   }

   /**
    * Reads a packet given a {@link PNInputStreamReader}. Returns true if the packet is successfully read.
    *
    * @return Returns true if full packet is successfully read, false otherwise
    * @throws IOException
    * @throws IndexOutOfBoundsException
    */
   private boolean readPacket(PNInputStreamReader reader) throws IOException, IndexOutOfBoundsException {
      int available = reader.available();
      // parse from reader
      if (available >= PNPackets.LENGTH_BYTES) {
         int length = (in.peekU(0) << 24)
            | (in.peekU(1) << 16)
            | (in.peekU(2) << 8)
            | in.peekU(3);

         // make sure we have parse the whole payload
         if (available - PNPackets.LENGTH_BYTES >= length) {
            // skip over the length bytes
            in.skip(PNPackets.LENGTH_BYTES);

            // if the packet has no ID, close the connection
            if (length < PNPackets.ID_BYTES) {
               in.skip(length);
               PN.storeAndLogError(new RuntimeException("Missing packet ID"), this,
                  "Failed to parse packet ID! Closing connection.");
               close();
            }
            else {
               this.receivePacket(reader.readShort(), reader);
               return true;
            }
         }
      }
      return false;
   }

   public synchronized void receivePacket(short packetId, PNInputStreamReader reader) {
      synchronized (in.getBufferLock()) {
         if (this.packetReceiver != null) {
            this.packetReceiver.receivePacket(packetId, reader);
         }
      }
   }

   public synchronized boolean sendPacket(PNPacket packet) {
      return out.write(packet, this.writer);
   }

   private synchronized void onInputStreamClosed() {
      if (isClosed()) {
         return;
      }

      PN.info(this, this + " Input stream closed! Closing connection");
      this.close();
   }

   private synchronized void onOutputStreamClosed() {
      if (isClosed()) {
         return;
      }

      PN.info(this, this + " Output stream closed! Closing connection");
      this.close();
   }

   public PNAsyncBufferedInputStream getInputStream() {
      return this.in;
   }

   public PNPacketOutputStream getOutputStream() {
      return this.out;
   }

   public PNInputStreamReader getReader() {
      return this.reader;
   }

   public PNOutputStreamWriter getWriter() {
      return this.writer;
   }

   public synchronized int getPort() {
      return this.socket.getPort();
   }

   public synchronized String getHost() {
      return this.socket.getInetAddress().getHostName();
   }

   @Override
   public synchronized PNErrorStorage getErrorStorage() {
      return this.errorStorage;
   }

   public synchronized boolean didDisconnectSelf() {
      return this.disconnectedSelf;
   }

   public synchronized boolean disconnect() {
      synchronized (closeLock) {
         if (isClosed()) {
            return false;
         }
         if (isConnected()) {    // this checks the underlying socket
            disconnectedSelf = true;
         }
         close();
         return disconnectedSelf;
      }
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   public synchronized boolean isConnected() {
      if (socket == null) return false;
      return !isClosed() && socket.isConnected() && !socket.isClosed();
   }

   public void close() {
      synchronized (closeLock) {
         if (this.isClosed()) return;
         this.closed = true;
      }

      synchronized (this) {
         PN.info(this, "[CLOSE " + this + "] Closing connection...");

         PN.info(this, "[CLOSE " + this + "] Closing input...");
         try {
            this.in.close(false);
         }
         catch (Exception e) {
            PN.error(e, "FAILED TO CLOSE INPUT");
         }

         PN.info(this, "[CLOSE " + this + "] Closing output...");
         try {
            this.out.close(false);
         }
         catch (Exception e) {
            PN.error(e, "FAILED TO CLOSE OUTPUT");
         }

         PN.info(this, "[CLOSE " + this + "] Closing socket...");
         try {
            this.socket.close();
         }
         catch (IOException e) {
            PN.error(e, this, "Failed to close socket when closing connection!");
         }

         PN.info(this, "[CLOSE " + this + "] Finished!");
      }
   }

   public ConnectionId getId() {
      return this.id;
   }

   @Override
   public String toString() {
      synchronized (this) {
         boolean closed = isClosed();
         PNError error = this.getErrorStorage().getTopError();
         return "PNConnection[id=" + id.data + ", host=" + getHost() + ":" + getPort() + ", "
            + (closed == true ? "closed" : "open") + (error != null ? ", ERROR" : "") + "]";
      }
   }

   public static class ConnectionId {
      private static long nextId = 0;

      private long data = 0;

      private ConnectionId() {
      }

      public long getData() {
         return this.data;
      }

      private synchronized static ConnectionId next() {
         ConnectionId id = new ConnectionId();
         nextId++;
         id.data = nextId;
         return id;
      }

      @Override
      public String toString() {
         return "ConnId[" + this.getData() + "]";
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         ConnectionId that = (ConnectionId)o;
         return data == that.data;
      }
      @Override
      public int hashCode() {
         return Objects.hash(data);
      }
   }

   public static class SocketResolver implements PNErrorStorable {

      private Socket socket;
      private PNErrorStorage errorStorage = new PNErrorStorage();
      private boolean success;

      public SocketResolver(Socket socket) {
         this.socket = socket;
         this.success = true;
      }

      public SocketResolver(String errorMessage) {
         this.errorStorage.add(new PNError(errorMessage));
         this.success = false;
      }

      public Socket getSocket() {
         return this.socket;
      }

      public boolean didSucceed() {
         return this.success;
      }

      @Override
      public PNErrorStorage getErrorStorage() {
         return this.errorStorage;
      }
   }

   public static class ServerSocketResolver implements PNErrorStorable {

      private ServerSocket serverSocket;
      private PNErrorStorage errorStorage = new PNErrorStorage();
      private boolean success;

      public ServerSocketResolver(ServerSocket serverSocket) {
         this.serverSocket = serverSocket;
         this.success = true;
      }

      public ServerSocketResolver(String errorMessage) {
         this.errorStorage.add(new PNError(errorMessage));
         this.success = false;
      }

      public ServerSocket getServerSocket() {
         return this.serverSocket;
      }

      public boolean didSucceed() {
         return this.success;
      }

      @Override
      public PNErrorStorage getErrorStorage() {
         return this.errorStorage;
      }
   }

}
