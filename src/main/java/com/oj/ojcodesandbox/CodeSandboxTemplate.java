package com.oj.ojcodesandbox;

import cn.hutool.core.io.FileUtil;
import com.oj.ojcodesandbox.enums.JudgeInfoMessageEnum;
import com.oj.ojcodesandbox.enums.QuestionSubmitStatusEnum;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteCodeResponse;
import com.oj.ojcodesandbox.model.ExecuteMessage;
import com.oj.ojcodesandbox.model.JudgeInfo;
import com.oj.ojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 运行代码方法模板
 */
@Slf4j
public abstract class CodeSandboxTemplate implements CodeSandBox{

    public static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    public static final String DEFAULT_FILE_NAME = "Main.java";

    public static final String suffix = ".in";

    public static final long TIME_OUT = 3000L;

    /**
     * 判题初始化，保存代码和输入用例为文件
     * @param code 用户代码
     * @return 代码文件
     */
    public File judgeInit(String code, List<String> inputs) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //  判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        //  将用户的代码隔离存放
        String judgeDirPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = judgeDirPath + File.separator + DEFAULT_FILE_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);


        for (int i = 0; i < inputs.size(); i ++) {
            String input = inputs.get(i);
            String inputFilePath = judgeDirPath + File.separator + i + suffix;
            FileUtil.writeString(input, inputFilePath, StandardCharsets.UTF_8);
        }

        return userCodeFile;
    }

    /**
     * 编译代码
     * @param userCodeFile 用户代码文件
     * @return 编译信息
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.getProcessResult(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行代码
     * @return 执行结果
     */
    public List<ExecuteMessage> runCode(File userCodeFile, List<String> inputs) {
        List<ExecuteMessage> result = new ArrayList<>();
        for (String input : inputs) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main", userCodeFile.getParent());
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //  超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ExecuteMessage message = ProcessUtils.getInteractProcessResult(runProcess, input);
                result.add(message);
            } catch (IOException e) {
                throw new RuntimeException("执行错误", e);
            }
        }
        return result;
    }

    /**
     * 根据沙箱输出获取判题结果
     * @param executeMessageList 执行结果
     * @return 输出结果
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        long totalTime = 0;
        long memory = 0;
        List<String> outputs = new ArrayList<>();
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (errorMessage != null) {
                //  执行中存在错误
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                break;
            }
            outputs.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            memory = Math.max(executeMessage.getMemory(), memory);
            if (time != null) {
                totalTime += time;
            }
        }

        //  程序正常运行完成
        if (outputs.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        }

        executeCodeResponse.setOutputs(outputs);

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(totalTime);
        judgeInfo.setMemory(memory);

        executeCodeResponse.setJudgeInfo(judgeInfo);

        return executeCodeResponse;
    }

    /**
     * 删除文件
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile());
            System.out.println("删除" + (del ? "成功": "失败"));
            return del;
        }
        return true;
    }

    @Override
    public ExecuteCodeResponse executeCodeResponse(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputs = executeCodeRequest.getInputs();
        String code = executeCodeRequest.getCode();

        File file = judgeInit(code, inputs);

        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ExecuteMessage compileFileExecuteMessage = compileFile(file);

        if (compileFileExecuteMessage.getExitValue() == 2) {
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setMessage(JudgeInfoMessageEnum.COMPLATE_ERROR.getValue());
            executeCodeResponse.setJudgeInfo(judgeInfo);
            //  虽然编译失败，但是执行操作是成功的
            executeCodeResponse.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
            return executeCodeResponse;
        }

        List<ExecuteMessage> executeMessageList = runCode(file, inputs);
        for (ExecuteMessage executeMessage : executeMessageList) {
            System.out.println("runcode结果： " + executeMessage.toString());
        }

        executeCodeResponse = getOutputResponse(executeMessageList);
        System.out.println("getOutputResponse结果： " + executeCodeResponse);

        boolean deleted = deleteFile(file);

        if (!deleted) {
            log.error("Delete file error，userCodeFilePath = {}", file.getAbsolutePath());
        }

        return executeCodeResponse;
    }

    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputs(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //  表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}
