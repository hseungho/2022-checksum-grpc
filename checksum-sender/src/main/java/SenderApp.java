import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Class SenderApp은 Checksum을 산출해내는 프로그램을 구현한 클래스다.
 * 데이터 전송 프로세스 중 송신 프로세스(호스트)에 해당하며, 사용자로부터 입력받은
 * 코드와 데이터를 통해 checksum을 산출해낸 후, 수신 프로세스(호스트)에게 전송한다.
 *
 * 송수신 프로세스 간 통신은 gRPC를 기반으로 한다.
 *
 * @author Seung ho Hwang
 */
public class SenderApp {

    private static SenderApp instance;
    private Scanner sc;
    private Long code;
    private Long data;
    private String checksum;
    private ManagedChannel channel;
    private TransportDataGrpc.TransportDataBlockingStub stub;
    private Boolean isRun;

    /**
     * Constructor of Class.
     * When this constructor is called, the code and data variable initializes to 0,
     * the isRun variable initializes to true, sc variable initializes to new Scanner
     * instance. And, it calls initGrpcConfig Method for connection with
     * server(receiver) process.
     */
    public SenderApp() {
        code = 0L;
        data = 0L;
        isRun = true;
        sc = new Scanner(System.in);
        initGrpcConfig();
    }

    private void initGrpcConfig() {
        channel = ManagedChannelBuilder
                .forAddress("localhost", 8080)
                .usePlaintext()
                .build();
         stub = TransportDataGrpc.newBlockingStub(channel);
    }

    public static SenderApp getInstance() {
        if(instance == null) {
            instance = new SenderApp();
        }
        return instance;
    }

    /**
     * Method for start this application.
     * It calls inputAll Method for input code and data from user.
     * And check if Run variable is true, and if it is true,
     * call show Menu Method to show the menu to the user.
     * Finally, if the user wants to terminate the program,
     * isRun variable changes to false, and calls the shutdown method
     * for server and program shutdown.
     */
    public void start() {
        System.out.println("----------------------------------------------");
        System.out.println(">>>>>>>>>>>>>>>>>>> SENDER <<<<<<<<<<<<<<<<<<<");
        System.out.println("----------------------------------------------\n");

        inputAll();
        while(isRun) {
            showMenu();
        }
        shutdown();
    }

    private void inputAll() {
        code = 0L;
        data = 0L;
        inputCode();
        inputData();
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

    private void inputData() {
        System.out.println("데이터를 입력해주세요.");
        while(true) {
            try {
                System.out.print("DATA: ");
                data = sc.nextLong(2);
                break;
            } catch (InputMismatchException | NumberFormatException e) {
                sc.nextLine();
                System.out.println("ERROR: 2진수의 숫자를 입력해주세요.");
            }
        }
    }

    private void showMenu() {
        System.out.println("\n>>>>>> MENU <<<<<<");
        System.out.println("1. 정상 데이터 전송");
        System.out.println("2. 오류 데이터 전송");
        System.out.println("3. 코드 / 데이터 재입력");
        System.out.println("0. 프로그램 종료");
        loop: while(true) {
            System.out.print("메뉴 입력: ");
            int selectedMenu = -999;
            try {
                selectedMenu = sc.nextInt();
            } catch (InputMismatchException | NumberFormatException ignored) {
                sc.nextLine();
            }
            System.out.println();
            switch (selectedMenu) {
                case 1 -> {
                    transportData(data);
                    break loop;
                }
                case 2 -> {
                    int error = (int)((Math.random()*10000)%10);
                    transportData(data+error);
                    break loop;
                }
                case 3 -> inputAll();
                case 0 -> {
                    isRun = false;
                    break loop;
                }
                default -> System.out.println("ERROR: 올바른 메뉴를 선택해주세요.");
            }
        }
    }

    /**
     * Method for calculating checksum value with code and data what it inputs from user.
     * First, it obtains code digits for XOR calculation.
     * Second, it calculates XOR calculation of data and code to get checksum value.
     * Finally, returns checksum value.
     *
     * @return checksum value of String variable.
     */
    private String getChecksum() {
        int count = (int)((Math.log10(Double.parseDouble(Long.toBinaryString(code)))));
        Long cal_data = data << count;

        // XOR 연산하기 위해 데이터를 String 변수로 변환.
        StringBuilder dataString = new StringBuilder(Long.toBinaryString(cal_data));
        String remainDataForChecksum = dataString.substring(count+1, dataString.length());
        String[] remainDataVector = remainDataForChecksum.split("");

        // calculating checksum
        Long calculatingChecksum = Long.parseLong(dataString.substring(0, count+1), 2) ^ code;
        String dividend;
        for (String s : remainDataVector) {
            dividend = Long.toBinaryString(calculatingChecksum) + s;
            // case 1: 연산 값의 맨 앞이 1인 경우, 연산값과 code 간의 XOR 연산
            if (dividend.length() > count) calculatingChecksum = Long.parseLong(dividend, 2) ^ code;
            // case 2: 연산 값의 맨 앞이 0인 경우, 연산값과 0 간의 XOR 연산
            else calculatingChecksum = Long.parseLong(dividend, 2);
        }

        StringBuilder checksumSb = new StringBuilder(Long.toBinaryString(calculatingChecksum));
        for(int i = checksumSb.length(); i < count; i++) {
            checksumSb.insert(0, "0");
        }
        checksum = checksumSb.toString();
        System.out.println("---------------------------------------------------");
        System.out.println("CHECKSUM: " + checksum);
        return checksum;
    }

    private int transportCount = 0;
    private void transportData(Long data) {
        String sendData = Long.toBinaryString(data) + getChecksum();
        System.out.println("SEND DATA: " + sendData);
        TransportDataOuterClass.Response response;
        try {
            response = stub.transportingData(
                    TransportDataOuterClass.Request.newBuilder()
                            .setData(sendData)
                            .build()
            );
        } catch (StatusRuntimeException e) {
            System.out.println("ERROR: 수신 호스트를 찾을 수 없습니다.");
            System.out.println("---------------------------------------------------");
            return;
        }

        switch (response.getResult()) {
            case "DATA_SUCCESS" -> {
                System.out.println("SUCCESS: 데이터가 정상적으로 전송되었습니다.");
                System.out.println("---------------------------------------------------");
                transportCount = 0;
            }
            case "DATA_ERROR" -> {
                System.out.println("ERROR: 데이터의 전송에 오류가 발생하였습니다.");
                System.out.println(">>> 데이터를 재전송합니다.");
                System.out.println("---------------------------------------------------\n");
                if(++transportCount < 3) {
                    transportData(this.data);
                } else {
                    System.out.println("ERROR: 데이터 전송에 오류가 발생하여 데이터 전송을 중단합니다.");
                    System.out.println("---------------------------------------------------");
                    transportCount = 0;
                }
            }
            case "CODE_ERROR" -> {
                System.out.println("ERROR: 수신 호스트에서 코드가 설정되어 있지 않습니다.");
                System.out.println(">>> 현재 코드: "+Long.toBinaryString(code));
                System.out.println("---------------------------------------------------");
                transportCount = 0;
            }
        }
    }

    private void shutdown() {
        System.err.println(">>> 프로그램을 종료합니다");
        channel.shutdown();
        sc.close();
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.exit(0);
    }


}
