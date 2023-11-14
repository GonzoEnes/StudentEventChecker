package model.server;

import model.ModelManager;
import model.data.DBHelper;
import model.data.Data;
import model.server.hb.HeartBeat;
import resources.ResourceManager;
import ui.ServerUI;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class SendHeartBeat extends Thread{

    private static final int HBTIMER = 5;
    private HeartBeat hearbeat;
    private MulticastSocket mcastSocket;
    private boolean isRunning;

    public SendHeartBeat(HeartBeat hearbeat, MulticastSocket mcastSocket){
        this.hearbeat = hearbeat;
        this.mcastSocket = mcastSocket;
        this.isRunning = true;
    }

    @Override
    public void run(){
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);) {

            while(isRunning){
                try{
                    Thread.sleep(HBTIMER * 1000);
                } catch (InterruptedException e) {
                    this.isRunning = false;
                    throw new RuntimeException(e);
                }

                oos.writeObject(hearbeat);
                byte[] buffer = baos.toByteArray();
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length,
                        InetAddress.getByName("230.44.44.44"), 4444);
                mcastSocket.send(dp);
            }

        } catch (IOException e) {
            if (this.isRunning) {
                e.printStackTrace();
            }
            if (!mcastSocket.isClosed()) {
                mcastSocket.close();
            }
        }
    }
}

public class Server {
    public static final int TIMEOUT = 10; // seconds

    public static void main(String[] args) {

        ServerUI serverUI = null;
        try {
            ModelManager modelManager = new ModelManager(Integer.parseInt(args[0]), args[1]/*, Integer.parseInt(args[2])*/);
            serverUI = new ServerUI(modelManager);
        } catch (SQLException | IOException | InterruptedException e) {
            e.printStackTrace();
        }

        serverUI.start();
    }

    private final String dbDirectory;
    private final Data data;
    private int serverPort;
    TcpHandler tcpHandler;
    private AtomicReference<String> operationResult;
    private AtomicReference<Boolean> handlerClient;

    // multicast
    private MulticastSocket mcastSocket = null;
    private NetworkInterface networkInterface;
    private InetAddress groupIp;
    private SocketAddress socketAddr;
    // multicast

    private HeartBeat heartBeat;
    SendHeartBeat sendHeartBeat;

    private String presenceList;

    public Server(int port, String dbDirectory /*, Integer rmiPort*/) throws SQLException, IOException {
        this.serverPort = port;
        this.dbDirectory = dbDirectory;

        // multicast
        this.mcastSocket = new MulticastSocket(Integer.parseInt(MULTICAST.getValue(1)));
        this.groupIp = InetAddress.getByName(MULTICAST.getValue(0));
        this.socketAddr = new InetSocketAddress(groupIp, Integer.parseInt(MULTICAST.getValue(1)));
        this.networkInterface = NetworkInterface.getByIndex(0);
        this.mcastSocket.joinGroup(socketAddr, networkInterface); // creates group for object comms
        // multicast

        this.heartBeat = new HeartBeat(port, true/*, 1, 1*/, dbDirectory);

        this.sendHeartBeat = new SendHeartBeat(this.heartBeat, mcastSocket);
        sendHeartBeat.start();

        this.data = new Data(new ResourceManager());

        this.tcpHandler = new TcpHandler();
        tcpHandler.start();

        operationResult = new AtomicReference<>("");
        handlerClient = new AtomicReference<>(true);
    }

    class TcpHandler extends Thread{

        @Override
        public void run(){
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                System.out.println("Server initialized successfully. Port used is " + serverSocket.getLocalPort() + ".");

                while (true) {
                    try {
                        Socket toClientSocket = serverSocket.accept();
                        toClientSocket.setSoTimeout(TIMEOUT * 1000);

                        InputStream is = toClientSocket.getInputStream();
                        OutputStream os = toClientSocket.getOutputStream();

                        byte[] msg = new byte[1024];
                        int nBytes = is.read(msg);
                        String msgReceived = new String(msg, 0, nBytes, StandardCharsets.UTF_8);

                        if(msgReceived.equals("CLIENT")){
                            Thread clientThread = new Thread(
                                    new RunnableClientThread(toClientSocket),
                                    toClientSocket.getInetAddress().toString()
                            );
                            clientThread.start();
                        }

                    } catch (Exception e) {
                        System.out.println("a");
                        System.out.println("Error: " + e);
                    }
                }
            } catch (Exception e) {
                System.out.println("b");
                System.out.println("Error: " + e);
            }
        }
    }

     class RunnableClientThread implements Runnable {
        private Socket socket;
        private DBHelper dbHelper;

        RunnableClientThread(Socket socket) {
            this.socket = socket;
            this.dbHelper = new DBHelper();
        }

        @Override
        public void run() {

            try(ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());){
                System.out.println("Client " + socket.getInetAddress() + ":" + socket.getPort());

                while (handlerClient.get()) {

                    operationResult.set("");

                    if ((this.dbHelper = (DBHelper) ois.readObject()) != null) {
                        System.out.println("\nServer received a new request from Client with\n\tIP:" + socket.getInetAddress().getHostAddress() + "\tPort: " + socket.getPort());
                    }


                    String requestResult = "";
                    while (!dbHelper.isRequestAlreadyProcessed()) {
                        switch (dbHelper.getOperation()) {
                            case "INSERT" -> {
                                switch (dbHelper.getTable()) {
                                    case "utilizador" -> {
                                        int id = data.insertUser(dbHelper.getParams());
                                        if (id == 0) {  //id quando é 0 falha
                                            requestResult = "false";
                                            dbHelper.setIsRequestAlreadyProcessed(true);
                                        } else {
                                            requestResult = id + "true";
                                            dbHelper.setIsRequestAlreadyProcessed(true);
                                        }
                                    }
                                    case "evento" -> {
                                        if (data.insertEvent(dbHelper.getParams()) == -1) {
                                            requestResult = "Event not created";
                                            dbHelper.setIsRequestAlreadyProcessed(true);
                                        } else {
                                            requestResult = "Event created";
                                            dbHelper.setIsRequestAlreadyProcessed(true);

                                        }
                                    }
                                    case "presenca" -> {

                                    }
                                }
                            }
                            case "SELECT" -> {
                                switch (dbHelper.getTable()) {
                                    case "utilizador" -> {
                                        int id = data.verifyLogin(dbHelper.getParams());
                                        if (id != 0) {
                                            requestResult = id + "User logged in";
                                            dbHelper.setIsRequestAlreadyProcessed(true);
                                        } else {
                                            requestResult = "User doesnt exist";
                                            dbHelper.setIsRequestAlreadyProcessed(true);
                                        }
                                    }
                                    case "evento" -> {
                                        requestResult = "PRESENCE LIST " + presenceList;
                                        dbHelper.setIsRequestAlreadyProcessed(true);
                                    }
                                    case "presenca" -> {


                                    }
                                    default -> System.out.println("default");
                                }
                            }
                            case "UPDATE" -> {
                                switch (dbHelper.getTable()) {
                                    case "utilizador" -> {
                                        if (data.editProfile(dbHelper.getParams(), dbHelper.getEmail())) {
                                            requestResult = "Update done";
                                            dbHelper.setIsRequestAlreadyProcessed(true);
                                        } else {
                                            requestResult = "Update failed";
                                            dbHelper.setIsRequestAlreadyProcessed(true);

                                        }
                                    }
                                }
                            }
                            default -> {
                                System.out.println("Erro!\n");
                            }
                        }
                    }

                    dbHelper.setRequestResult(requestResult);

                    while(true){
                        if(!this.dbHelper.getRequestResult().equals("")){
                            oos.writeObject(this.dbHelper.getRequestResult());
                            this.dbHelper.setRequestResult("");
                            break;
                        }
                    }

                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            /*try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error: " + e);
            }*/

        }
    }
}

