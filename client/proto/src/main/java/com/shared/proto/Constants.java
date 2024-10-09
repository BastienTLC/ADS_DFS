package com.shared.proto;

import com.devProblems.FileMetadata;
import io.grpc.Context;
import io.grpc.Metadata;

public class Constants {
    // file-meta-bin because we will we send it at bytes to the metadata
    public static final Metadata.Key<byte[]> fileMetadataKey = Metadata.Key.of("file-meta-bin", Metadata.BINARY_BYTE_MARSHALLER);

    public static final Context.Key<FileMetadata> fileMetaContext = Context.key("file-meta");
}
