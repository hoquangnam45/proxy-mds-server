package com.txtech.mds.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.txtech.mds.server.component.MdsContextHolder;
import com.txtech.mds.server.pojo.GenericResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

@RestController
@RequestMapping("api/context")
public class ContextController {
    private final MdsContextHolder mdsContextHolder;

    @Autowired
    public ContextController(MdsContextHolder mdsContextHolder) {
        this.mdsContextHolder = mdsContextHolder;
    }

    @PostMapping("{contextName}/publish/{schemaName}/{subSchemaName}")
    public ResponseEntity<GenericResponse> publishToContext(
            HttpServletRequest request,
            @PathVariable("schemaName") String schemaName,
            @PathVariable("subSchemaName") String subSchemaName,
            @PathVariable("contextName") String contextName,
            @RequestBody JsonNode data) throws IOException, InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {
        mdsContextHolder.getSocketControllers().get(contextName).publish(schemaName, subSchemaName, data);
        return ResponseEntity.ok(new GenericResponse(200, request.getServletPath(), "Publish successfully"));
    }

    @GetMapping("{contextName}/schemas")
    public ResponseEntity<Map<String, Map<String, JsonNode>>> getSchemas(@PathVariable("contextName") String contextName) {
        return ResponseEntity.ok(mdsContextHolder.getContexts().get(contextName).getSchemas());
    }

    @GetMapping("{contextName}/schemas/{schemaName}")
    public ResponseEntity<Map<String, JsonNode>> getSchema(
            @PathVariable("schemaName") String schemaName,
            @PathVariable("contextName") String contextName) {
        return ResponseEntity.ok(mdsContextHolder.getContexts().get(contextName).getSchemas().get(schemaName));
    }

    @GetMapping("{contextName}/schemas/{schemaName}/{subSchemaName}")
    public ResponseEntity<JsonNode> getSchema(
            @PathVariable("schemaName") String schemaName,
            @PathVariable("subSchemaName") String subSchemaName,
            @PathVariable("contextName") String contextName) {
        return ResponseEntity.ok(mdsContextHolder.getContexts().get(contextName).getSchemas().get(schemaName).get(subSchemaName));
    }
}
