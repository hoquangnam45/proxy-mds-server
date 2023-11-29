package com.txtech.mds.server.util;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import org.springframework.security.core.parameters.P;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class FileDescriptors {
    public static Descriptors.FileDescriptor buildFrom(DescriptorProtos.FileDescriptorSet fileDescriptorSet, String entry) throws Descriptors.DescriptorValidationException {
        Map<String, DescriptorProtos.FileDescriptorProto> fileDescriptorProtoMap = fileDescriptorSet.getFileList().stream().collect(Collectors.toMap(
                DescriptorProtos.FileDescriptorProto::getName,
                fileDescriptorProto -> fileDescriptorProto
        ));
        Map<String, Descriptors.FileDescriptor> resolvedDescriptors = new HashMap<>();
        resolve(fileDescriptorProtoMap.get(entry), resolvedDescriptors, fileDescriptorProtoMap);
        return resolvedDescriptors.get(entry);
    }

    private static void resolve(DescriptorProtos.FileDescriptorProto fileDescriptorProto, Map<String, Descriptors.FileDescriptor> resolvedDescriptors, Map<String, DescriptorProtos.FileDescriptorProto> descriptorProtoMap) throws Descriptors.DescriptorValidationException {
        try {
            for (String dependency : fileDescriptorProto.getDependencyList()) {
                if (!resolvedDescriptors.containsKey(dependency)) {
                    resolve(descriptorProtoMap.get(dependency), resolvedDescriptors, descriptorProtoMap);
                }
            }

            Descriptors.FileDescriptor[] dependencies = fileDescriptorProto.getDependencyList().stream()
                    .map(resolvedDescriptors::get)
                    .toArray(Descriptors.FileDescriptor[]::new);
            resolvedDescriptors.put(fileDescriptorProto.getName(), Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
