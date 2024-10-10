package com.grpc.server.fileupload.server.interceptor;

import com.devProblems.FileMetadata;
import com.google.protobuf.InvalidProtocolBufferException;
import com.shared.proto.Constants;
import io.grpc.*;
        import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

@GrpcGlobalServerInterceptor
public class FileUploadInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {

        if (metadata.containsKey(Constants.fileMetadataKey)) {
            byte[] metaBytes = metadata.get(Constants.fileMetadataKey);
            FileMetadata fileMetadata;
            try {
                // here we create the file metadata from the array that we got
                fileMetadata = FileMetadata.parseFrom(metaBytes);
            } catch (InvalidProtocolBufferException e) {
                Status status = Status.INTERNAL.withDescription("Unable to create file metadata");
                serverCall.close(status, metadata);
                return new ServerCall.Listener<ReqT>() {};
            }
            // intializing the context that will help us get the value of filemetadata in the service
            Context context = Context.current().withValue(
                    Constants.fileMetaContext,
                    fileMetadata
            );
            // intercepting the call and adding the context to it
            return Contexts.interceptCall(context, serverCall, metadata, serverCallHandler);
        } else {
            // if the metadata does not contain the filemetadata key, we just start the call
            return serverCallHandler.startCall(serverCall, metadata);
        }
    }
}

