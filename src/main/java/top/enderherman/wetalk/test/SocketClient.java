package top.enderherman.wetalk.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class SocketClient {
    public static void main(String[] args) {
        Socket socket = null;
        try {
            socket = new Socket("127.0.0.1", 1024);


            OutputStream outputStream = socket.getOutputStream();
            PrintWriter printWriter = new PrintWriter(outputStream);
            System.out.println("请输入信息");
            Scanner scanner = new Scanner(System.in);
            new Thread(() -> {
                while (true) {
                    String input = scanner.nextLine();
//                    if (input.equals("exit")){
//                        System.out.println("服务已结束");
//                        break;
//                    }
                    try {
                        printWriter.println(input);
                        printWriter.flush();
                    }catch (Exception e){
                        e.printStackTrace();
                        break;
                    }

                }
            }).start();

            BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(socket.getInputStream()));

            new Thread(() -> {
                while (true) {
                    try {
                        String readData = bufferedReader.readLine();
                        System.out.println("服务端回复: "+readData);
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }

                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
