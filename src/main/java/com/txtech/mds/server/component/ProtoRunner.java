package com.txtech.mds.server.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

@Getter
@RequiredArgsConstructor
public class ProtoRunner {
    private final File descriptorSetFile;
    private final File inputFile;

    public int run() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "protoc",
                MessageFormat.format("--proto_path={0}", inputFile.getParentFile().getAbsolutePath()),
                MessageFormat.format("--descriptor_set_out={0}", descriptorSetFile.getAbsolutePath()),
                "--include_imports",
                inputFile.getAbsolutePath());
        Process p = pb.inheritIO().start();
        return p.waitFor();
    }
}
