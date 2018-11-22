package com.medleystudios.pn.conn;

import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.util.PNUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class PNConnection {

   private final Object closeLock = new Object();

   private ConnectionID id;
   private boolean closed = false;
   private Socket socket;
   private PNInputStreamReader inReader;
   private PNOutputStreamWriter outWriter;
   private String errorMessageIO = null;

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
            PNUtil.error(e, "Failed to host server " + host + ":" + port);
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
            PNUtil.error(e, "Failed to connect to " + host + ":" + port);
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
         PNUtil.fatalError(e, this, "Failed to get socket input stream " + this.socket);
      }

      try {
         out = this.socket.getOutputStream();
      }
      catch (IOException e) {
         PNUtil.fatalError(e, this, "Failed to get socket output stream " + this.socket);
      }

      this.inReader = new PNInputStreamReader(in, () -> {
         // ON CLOSE
         synchronized (this) {
            // We don't need to run anything here if already closed
            if (isClosed()) {
               return;
            }

            PNUtil.log(this, this + " Reader closed! Closing connection");
            this.close();
         }
      });
      this.outWriter = new PNOutputStreamWriter(out, () -> {
         // ON CLOSE
         synchronized (this) {
            // We don't need to run anything here if already closed
            if (isClosed()) {
               return;
            }

            PNUtil.log(this, this + " Writer closed! Closing connection");
            this.close();
         }
      });

      new Thread(this.outWriter).start();
      new Thread(this.inReader).start();
   }

   public synchronized PNOutputStreamWriter getWriter() {
      return this.outWriter;
   }

   public synchronized PNInputStreamReader getReader() {
      return this.inReader;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   private synchronized boolean isOutputShutdown() {
      return this.socket.isOutputShutdown();
   }

   public synchronized String getErrorMessage() {
      return errorMessageIO;
   }

   public synchronized boolean isConnected() {
      return this.socket != null && this.socket.isConnected();
   }

   /**
    * Attempts to disconnect in an orderly fashion. If that fails, the connection is closed abortive.
    */
   public synchronized void disconnect() {
      PNUtil.log(this, "Issuing disconnect on: " + this.socket);
      if (!orderlyRelease()) {
         PNUtil.log(this, "Orderly connection release failed! Closing connection abortive: " + this.socket);
         this.close();
      }
      else {
         PNUtil.log(this, "Successfully disconnected: " + this.socket);
      }
   }

   public synchronized boolean orderlyRelease() {
      if (this.isClosed() || !this.isConnected() || this.isOutputShutdown()) return false;

      try {
         this.socket.shutdownOutput();
      }
      catch (IOException e) {
         PNUtil.error(e, this, "Failed to shut down output stream!");
         return false;
      }

      return true;
   }

   public synchronized void close() {
      synchronized (closeLock) {
         if (this.isClosed()) return;
         this.closed = true;

         PNUtil.log(this, "[CLOSE " + this + "] Closing connection...");

         PNUtil.log(this, "[CLOSE " + this + "] Closing reader...");
         this.inReader.close(false);

         PNUtil.log(this, "[CLOSE " + this + "] Closing writer...");
         this.outWriter.close(false);

         PNUtil.log(this, "[CLOSE " + this + "] Closing socket...");
         try {
            this.socket.close();
         }
         catch (IOException e) {
            PNUtil.error(e, this, "Failed to close socket when closing connection!");
         }

         PNUtil.log(this, "[CLOSE " + this + "] Getting error message IO if one exists...");
         if (this.inReader.getErrorMessage() != null) {
            this.errorMessageIO = this.inReader.getErrorMessage();
         }
         else if (this.outWriter.getErrorMessage() != null) {
            this.errorMessageIO = this.outWriter.getErrorMessage();
         }

         PNUtil.log(this, "[CLOSE " + this + "] Finished!");
      }
   }

   @Override
   public String toString() {
      String errorMessageIOString = errorMessageIO == null ? "" : ", errorMessageIO: " + errorMessageIO;
      return "PNConnection[" + id + ", " + socket + ", closed: " + isClosed() + errorMessageIOString + "]";
   }

   public static class ConnectionID {
      private static long nextId = 0;

      private long data = 0;

      private ConnectionID() {
      }

      public long getData() {
         return this.data;
      }

      private synchronized static ConnectionID next() {
         ConnectionID id = new ConnectionID();
         nextId++;
         id.data = nextId;
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

   public static class SocketResolver {

      private Socket socket;
      private String errorMessage;
      private boolean success;

      public SocketResolver(Socket socket) {
         this.socket = socket;
         this.success = true;
      }

      public SocketResolver(String errorMessage) {
         this.errorMessage = errorMessage;
         this.success = false;
      }

      public Socket getSocket() {
         return this.socket;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }

      public boolean didSucceed() {
         return this.success;
      }

   }

   public static class ServerSocketResolver {

      private ServerSocket serverSocket;
      private String errorMessage;
      private boolean success;

      public ServerSocketResolver(ServerSocket serverSocket) {
         this.serverSocket = serverSocket;
         this.success = true;
      }

      public ServerSocketResolver(String errorMessage) {
         this.errorMessage = errorMessage;
         this.success = false;
      }

      public ServerSocket getServerSocket() {
         return this.serverSocket;
      }

      public String getErrorMessage() {
         return this.errorMessage;
      }

      public boolean didSucceed() {
         return this.success;
      }

   }

}
