import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Class ReceiverApp는 Checksum 기능을 검증하는 프로그램을 구현한 클래스다.
 * 데이터 전송 프로세스 중 수신 프로세스(호스트)에 해당하며, 송신 프로세스(호스트)로부터
 * 전송된 데이터를 수신하여 데이터로부터 Checksum 검증을 실시한다.
 * 검증 시, checksum 값이 0일 경우, 데이터 전송이 정상적으로 성공했다는 의미이다.
 * checksum 값이 0이 아닐 경우, 데이터 전송에 오류가 발생했다는 의미이다.
 *
 * 송수신 프로세스 간 통신은 gRPC를 기반으로 한다.
 *
 * @author Seung ho Hwang
 */
public class ReceiverApp {

    private static ReceiverApp instance;
    private Server server;
    private final Scanner sc;
    private Long code = 0L;
    private Boolean isRun;

    /**
     * Constructor of Class.
     * When this constructor is called, the isRun variable initializes to true,
     * sc variable initializes to new Scanner instance,
     * and it calls startServer Method for starting gRPC server.
     */
    public ReceiverApp() {
        isRun = true;
        sc = new Scanner(System.in);
        startServer();
    }

    public static ReceiverApp getInstance() {
        if(instance == null) {
            instance = new ReceiverApp();
        }
        return instance;
    }

    public boolean isExistCode() {
        return code != 0L;
    }

    /**
     * Method for start this application.
     * It calls inputCode Method for input code from user.
     * And check if Run variable is true, and if it is true,
     * call show Menu Method to show the menu to the user.
     * Finally, if the user wants to terminate the program,
     * isRun variable changes to false, and calls the shutdown method
     * for server and program shutdown.
     */
    public void start() {
        System.out.println("----------------------------------------------");
        System.out.println(">>>>>>>>>>>>>>>>>> RECEIVER <<<<<<<<<<<<<<<<<<");
        System.out.println("----------------------------------------------\n");

        inputCode();
        while(isRun){
            showMenu();
        }
        shutdown();
    }

    private void inputCode() {
        System.out.println("코드를 입력해주세요.");
        while(true) {
            try {
                System.out.print("CODE: ");
                code = sc.nextLong(2);
                break;
            } catch (InputMismatchException | NumberFormatException e) {
                sc.nextLine();
                System.out.println("ERROR: 2진수의 숫자를 입력해주세요.");
            }
        }
    }

    public void showMenu() {
        System.out.println("\n>>>>>> MENU <<<<<<");
        System.out.println("1. 코드 재입력");
        System.out.println("0. 프로그램 종료");
        int menu = -999;
        try {
            menu = sc.nextInt();
        } catch (InputMismatchException | NumberFormatException e) {
            sc.nextLine();
        }
        switch (menu) {
            case 1 -> inputCode();
            case 0 -> isRun = false;
            default -> {
                System.out.println("잘못된 메뉴입니다.");
                showMenu();
            }
        }
    }

    /**
     * Method for verifying checksum value from sender's data.
     * First, it obtains code digits for XOR calculation.
     * Second, it calculates XOR calculation of data and code to get checksum value.
     * Finally, verify that the checksum value is 0.
     *
     * @param receive data from sender
     * @return boolean value of verifying checksum. If it is true, app receive correct data,
     * But if it is false, app receive incorrect data.
     */
    public boolean verifyChecksum(String receive) {
        if(code == 0L) return false;

        System.out.println("\n-----------------------------------------------");
        System.out.println("RECEIVE DATA: " + receive);
        int count = (int)((Math.log10(Double.parseDouble(Long.toBinaryString(code)))));

        // XOR 연산하기 위해 데이터를 String 변수로 변환.
        StringBuilder receiveString = new StringBuilder(receive);
        String remainReceiverForVerify = receiveString.substring(count+1, receiveString.length());
        String[] remainReceiverVector = remainReceiverForVerify.split("");

        // Checksum 값을 얻기 위한 XOR 연산.
        long verifyingError = Long.parseLong(receiveString.substring(0, count+1), 2) ^ code;
        String dividend;
        for (String s : remainReceiverVector) {
            dividend = Long.toBinaryString(verifyingError) + s;
            // case 1: 연산 값의 맨 앞이 1인 경우, 연산값과 code 간의 XOR 연산
            if (dividend.length() > count) verifyingError = Long.parseLong(dividend, 2) ^ code;
            // case 2: 연산 값의 맨 앞이 0인 경우, 연산값과 0 간의 XOR 연산
            else verifyingError = Long.parseLong(dividend, 2);
        }

        StringBuilder verifiedErrorSb = new StringBuilder(Long.toBinaryString(verifyingError));
        for(int i = verifiedErrorSb.length(); i < count; i++) {
            verifiedErrorSb.insert(0, "0");
        }
        System.out.println("VERIFIED RESULT: " + verifiedErrorSb);
        if(verifyingError == 0L) {
            System.out.println("SUCCESS: 데이터를 정상적으로 수신하였습니다.");
            System.out.println("-----------------------------------------------");
            return true;
        }
        else {
            System.out.println("ERROR: 올바르지 못한 데이터를 수신하였습니다.");
            System.out.println("-----------------------------------------------");
            return false;
        }
    }

    private void startServer() {
        Thread serverThread = new Thread(() -> {
            int port = 8080;
            server = NettyServerBuilder
                    .forAddress(new InetSocketAddress("localhost", port))
                    .addService(new TransportDataImpl())
                    .build();

            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("\nServer: gRPC 서버를 종료합니다.");
                System.err.println("Server: 서버를 종료합니다.");
                System.err.println(">>> 프로그램이 종료됩니다.");
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (!server.isTerminated()) {
                    server.shutdown();
                }
            }));

            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
    }

    private void shutdown() {
        sc.close();
        if(!server.isTerminated()) {
            server.shutdown();
        }
    }

}
