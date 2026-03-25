package top.enderherman.wetalk.test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class SocketServer {
    public static void main(String[] args) {
        ServerSocket server = null;
        try {
            server = new ServerSocket(1024);
            System.out.println("等待客户端链接");
            Map<String, Socket> clientMap = new HashMap<>();
            while (true) {
                Socket socket = server.accept();
                String ip = socket.getInetAddress().getHostAddress();
                System.out.println("客户端链接 ip" + ip + " 端口：" + socket.getPort());
                String clientKey = ip + socket.getPort();
                clientMap.put(clientKey, socket);
                new Thread(() -> {
                    while (true) {
                        try {
                            InputStream inputStream = socket.getInputStream();
                            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                            String fileData = bufferedReader.readLine();
                            System.out.println("客户端消息：" + fileData);
//1.单回消息
//                            PrintWriter printWriter = new PrintWriter(socket.getOutputStream());
//                            printWriter.println("我是服务器，你上早八,message: " + fileData);
//                            printWriter.flush();


                            clientMap.forEach((k, v) -> {
                                try {
                                    OutputStream outputStream = v.getOutputStream();
                                    PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(outputStream));
                                    printWriter.println(socket.getPort()+ "speak: " + fileData);
                                    printWriter.flush();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            });


                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
