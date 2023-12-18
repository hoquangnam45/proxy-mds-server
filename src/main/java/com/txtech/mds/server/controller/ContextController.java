package com.txtech.mds.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.txtech.mds.msg.type.MsgBaseMessage;
import com.txtech.mds.server.component.JsonMdsPublisher;
import com.txtech.mds.server.component.MdsContextHolder;
import com.txtech.mds.server.pojo.GenericResponse;
import com.txtech.mds.server.pojo.IPublisher;
import com.txtech.mds.server.pojo.MdsContext;
import com.txtech.mds.server.pojo.MdsPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("api/context")
public class ContextController {
    private final MdsContextHolder mdsContextHolder;

    @Autowired
    public ContextController(MdsContextHolder mdsContextHolder) {
        this.mdsContextHolder = mdsContextHolder;
    }

    @PostMapping("{contextName}/publish/{interfaceClass}/{implementedClass}")
    public ResponseEntity<GenericResponse> publishToContext(
            HttpServletRequest request,
            @PathVariable("interfaceClass") String interfaceClass,
            @PathVariable("implementedClass") String implementedClass,
            @PathVariable("contextName") String contextName,
            @RequestBody JsonNode data) throws Exception {
        MdsContext mdsContext = mdsContextHolder.getSocketControllers().get(contextName).getMdsContext();
        IPublisher<MsgBaseMessage> delegatePublisher = mdsContextHolder.getSocketControllers().get(contextName);
        new JsonMdsPublisher(mdsContext.getSchemaClasses(), mdsContext.getObjectMapper(), delegatePublisher)
                .publish(new MdsPayload<>(interfaceClass, implementedClass, data));
        return ResponseEntity.ok(new GenericResponse(200, request.getServletPath(), "Publish successfully"));
    }

    @GetMapping("{contextName}/schemas")
    public ResponseEntity<Map<String, Map<String, ObjectNode>>> getSchemas(@PathVariable("contextName") String contextName) {
        return ResponseEntity.ok(mdsContextHolder.getContexts().get(contextName).getJsonSchemas());
    }

    @GetMapping("{contextName}/schemas/{interfaceClass}")
    public ResponseEntity<Map<String, ObjectNode>> getSchema(
            @PathVariable("interfaceClass") String interfaceClass,
            @PathVariable("contextName") String contextName) {
        return ResponseEntity.ok(mdsContextHolder.getContexts().get(contextName).getJsonSchemas().get(interfaceClass));
    }

    @GetMapping("{contextName}/schemas/{interfaceClass}/{implementedClass}")
    public ResponseEntity<JsonNode> getSchema(
            @PathVariable("interfaceClass") String interfaceClass,
            @PathVariable("implementedClass") String implementedClass,
            @PathVariable("contextName") String contextName) {
        return ResponseEntity.ok(mdsContextHolder.getContexts().get(contextName).getJsonSchemas().get(interfaceClass).get(implementedClass));
    }

    @GetMapping("{contextName}/clients/count")
    public ResponseEntity<GenericResponse> getClientCount(
            @PathVariable("contextName") String contextName,
            HttpServletRequest request) {
        String path = request.getServletPath();
        if (mdsContextHolder.getSocketControllers().get(contextName) == null) {
            return ResponseEntity.status(404).body(new GenericResponse(404, path, "No context name " + contextName));
        }
        return ResponseEntity.ok(new GenericResponse(200, path, "" + mdsContextHolder.getSocketControllers().get(contextName).getActiveClients().size()));
    }
}
