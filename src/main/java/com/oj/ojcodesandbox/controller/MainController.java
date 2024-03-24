package com.oj.ojcodesandbox.controller;

import com.oj.ojcodesandbox.JavaDockerCodeSandbox;
import com.oj.ojcodesandbox.PythonDockerCodeSandbox;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/")
public class MainController {

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        String language = executeCodeRequest.getLanguage();
        switch (language) {
            case "java":
                return new JavaDockerCodeSandbox().executeCodeResponse(executeCodeRequest);
            case "python":
                return new PythonDockerCodeSandbox().executeCodeResponse(executeCodeRequest);
            default:
                return new ExecuteCodeResponse();
        }
    }
}
