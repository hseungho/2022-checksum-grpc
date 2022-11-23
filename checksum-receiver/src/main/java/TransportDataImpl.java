import io.grpc.stub.StreamObserver;

/**
 * Class TransportDataImpl is a class that implements TransportData proto file.
 * It receives data from client(sender) process, and delivers to ReceiverApp Instance for
 * verifying data error with checksum value.
 */
public final class TransportDataImpl extends TransportDataGrpc.TransportDataImplBase {
    private ReceiverApp app = ReceiverApp.getInstance();

    @Override
    public void transportingData(TransportDataOuterClass.Request request, StreamObserver<TransportDataOuterClass.Response> responseObserver) {
        TransportDataOuterClass.Response response;
        String receive = request.getData();

        if(!app.isExistCode()) {
            response = TransportDataOuterClass.Response.newBuilder()
                    .setResult("CODE_ERROR")
                    .build();
        } else {
            if (app.verifyChecksum(receive)) {
                response = TransportDataOuterClass.Response.newBuilder()
                        .setResult("DATA_SUCCESS")
                        .build();
            } else {
                response = TransportDataOuterClass.Response.newBuilder()
                        .setResult("DATA_ERROR")
                        .build();
            }
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}