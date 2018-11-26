package com.medleystudios.pn.conn;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.packets.PNPackets;
import com.medleystudios.pn.conn.packets.PNPingPacket;
import com.medleystudios.pn.conn.packets.PNPongPacket;
import com.medleystudios.pn.io.PNAsyncBufferedOutputStream;
import com.medleystudios.pn.io.PNAsyncBufferedInputStream;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.io.PNPacketOutputStream;
import com.medleystudios.pn.util.PNUtil;
import com.medleystudios.pn.util.error.PNError;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static com.medleystudios.pn.util.PNUtil.u;
import static java.util.concurrent.CompletableFuture.supplyAsync;

public class PNConnection implements PNErrorStorable {

   private final Object closeLock = new Object();

   private ConnectionID id;
   private boolean closed = false;
   private Socket socket;
   private PNAsyncBufferedInputStream in;
   private PNPacketOutputStream out;

   private PNInputStreamReader<PNAsyncBufferedInputStream> reader;
   private PNOutputStreamWriter<PNPacketOutputStream> writer;

   private PNErrorStorage errorStorage = new PNErrorStorage();

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
      this.id = ConnectionID.next();
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

      this.in = PNAsyncBufferedInputStream.start(in, this::onInputStreamClosed);
      this.out = PNPacketOutputStream.start(out, this::onOutputStreamClosed);
      this.errorStorage
         .addChild(this.in.getErrorStorage())
         .addChild(this.out.getErrorStorage());

      this.reader = new PNInputStreamReader<>(this.in);
      this.writer = new PNOutputStreamWriter<>(this.out);
   }

   public synchronized void process() {
      synchronized (in.getBufferLock()) {
         int available = in.available();
         // read from reader
         if (available >= PNPackets.LENGTH_BYTES) {
            int length = (in.peekU(0) << 24) | (in.peekU(1) << 16) | (in.peekU(2) << 8) | in.peekU(3);

            // make sure we have read the whole payload
            if (available - PNPackets.LENGTH_BYTES >= length) {
               // skip over the length bytes
               in.skip(PNPackets.LENGTH_BYTES);

               // if the packet has no ID, close the connection
               if (length < PNPackets.ID_BYTES) {
                  in.skip(length);
                  PN.storeAndLogError(new RuntimeException("Missing packet ID"), this,
                     "Failed to read packet ID! Closing connection.");
                  close();
               }
               else {

                  // This is where we should tell the owner of the connection to "receive" the packet
                  // That way, it's on the "connection owner" to parse the packet and handle it

                  // For now we'll just read it here for testing
                  try {
                     // read the id
                     short id = reader.readShort();
                     if (id == PNPackets.PING_PACKET) {
                        PNPingPacket packet = new PNPingPacket();
                        packet.read(reader);

                        PN.info("Received PING!");
                        for (int i = 0; i < packet.dates.size(); i++) {
                           PN.info(PN.toISO(packet.dates.get(i)));
                        }

                        synchronized (out.getBufferLock()) {
                           PNPongPacket response = new PNPongPacket();
                           response.date = new Date();
                           response.write(writer);
                        }
                     }
                     else if (id == PNPackets.PONG_PACKET) {
                        PNPongPacket packet = new PNPongPacket();
                        packet.read(reader);
                        PN.info("Received PONG: " + packet.date);
                     }
                  }
                  catch (IOException e) {
                     // TODO
                     // "connection owner" needs to handle this
                     PN.storeAndLogError(e, this, "Failed to process data due to IOException! Closing connection.");
                     close();
                  }
                  catch (IndexOutOfBoundsException e) {
                     // TODO
                     // "connection owner" needs to handle this
                     PN.storeAndLogError(e, this, "Failed to process data due to malformed data! Closing connection.");
                     close();
                  }
                  catch (Exception e) {
                     // TODO
                     // "connection owner" needs to handle this
                     PN.storeAndLogError(e, this, "Failed to process data for unexpected reason! Closing connection.");
                     close();
                  }
               }
            }
         }
      }

      synchronized (out.getBufferLock()) {
         // write to writer
      }
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

   public synchronized PNAsyncBufferedInputStream getInputStream() {
      return this.in;
   }

   public synchronized PNPacketOutputStream getOutputStream() {
      return this.out;
   }

   public synchronized PNInputStreamReader<PNAsyncBufferedInputStream> getReader() {
      return this.reader;
   }

   public synchronized PNOutputStreamWriter<PNPacketOutputStream> getWriter() {
      return this.writer;
   }

   @Override
   public PNErrorStorage getErrorStorage() {
      return this.errorStorage;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   public synchronized boolean isConnected() {
      if (socket == null) return false;
      return socket.isConnected() && !socket.isClosed();
   }

   public synchronized void close() {
      synchronized (closeLock) {
         if (this.isClosed()) return;
         this.closed = true;

         PN.info(this, "[CLOSE " + this + "] Closing connection...");

         PN.info(this, "[CLOSE " + this + "] Closing reader...");
         this.in.close(false);

         PN.info(this, "[CLOSE " + this + "] Closing writer...");
         this.out.close(false);

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

   @Override
   public String toString() {
      synchronized (this) {
         boolean closed = isClosed();
         PNError error = this.getErrorStorage().getTopError();
         return "PNConnection[" + id + " " + socket + " closed=" + closed + (error != null ? " " + error : "") + "]";
      }
   }

   public static class ConnectionID {
      private static long nextID = 0;

      private long data = 0;

      private ConnectionID() {
      }

      public long getData() {
         return this.data;
      }

      private synchronized static ConnectionID next() {
         ConnectionID id = new ConnectionID();
         nextID++;
         id.data = nextID;
         return id;
      }

      @Override
      public String toString() {
         return "ConnID[" + this.getData() + "]";
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         ConnectionID that = (ConnectionID)o;
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
      ;
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
         return null;
      }
   }

}
