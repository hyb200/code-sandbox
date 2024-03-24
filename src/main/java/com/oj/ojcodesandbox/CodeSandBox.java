package com.oj.ojcodesandbox;

import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteCodeResponse;

public interface CodeSandBox {

    ExecuteCodeResponse executeCodeResponse(ExecuteCodeRequest executeCodeRequest);
}
