package com.oj.ojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.dfa.WordTree;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteCodeResponse;
import com.oj.ojcodesandbox.model.ExecuteMessage;
import com.oj.ojcodesandbox.model.JudgeInfo;
import com.oj.ojcodesandbox.security.DefaultSecurityManager;
import com.oj.ojcodesandbox.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaNativeCodeSandbox implements CodeSandBox {

    public static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    public static final String DEFAULT_FILE_NAME = "Main.java";

    public static final long TIME_OUT = 3000L;

    private static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE;

    static {
        // 初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }


    @Override
    public ExecuteCodeResponse executeCodeResponse(ExecuteCodeRequest executeCodeRequest) {
        //  设置安全管理器，Java 8 以后废弃
        System.setSecurityManager(new DefaultSecurityManager());

        List<String> inputs = executeCodeRequest.getInputs();
        String code = executeCodeRequest.getCode();

        //  校验代码中是否包含黑名单中的敏感词
//        FoundWord foundWord = WORD_TREE.matchWord(code);
//        if (foundWord != null) {
//            System.out.println("包含敏感词：" + foundWord.getFoundWord());
//            return null;
//        }

        //  将用户代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //  判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        //  将用户的代码隔离存放
        String userCodeDirPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeDirPath + File.separator + DEFAULT_FILE_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //  编译代码
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ProcessUtils.getProcessResult(compileProcess, "编译");
        } catch (IOException e) {
            return getErrorResponse(e);
        }

        //  执行代码
        List<ExecuteMessage> result = new ArrayList<>();
        for (String input : inputs) {
            String runCmd = String.format("java -Dfile.encoding=UTF-8 -cp %s Main", userCodeDirPath);
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
                return getErrorResponse(e);
            }
        }

        //  整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        long totalTime = 0;
        List<String> outputs = new ArrayList<>();
        for (ExecuteMessage executeMessage : result) {
            String errorMessage = executeMessage.getErrorMessage();
            if (errorMessage != null) {
                //  执行中存在错误
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;
            }
            outputs.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                totalTime += time;
            }
        }
        //  程序正常运行完成
        if (outputs.size() == result.size()) {
            executeCodeResponse.setStatus(1);
        }

        executeCodeResponse.setOutputs(outputs);

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(totalTime);

        executeCodeResponse.setJudgeInfo(judgeInfo);

        //  文件清理
        if (userCodeFile.getParentFile() != null) {
            FileUtil.del(userCodeDirPath);
        }

        System.out.println(executeCodeResponse);
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

    public static void main(String[] args) {
        JavaNativeCodeSandbox javaNativeCodeSandBox = new JavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputs(Arrays.asList("1 2\n", "1 3\n"));

        String code = ResourceUtil.readStr("test/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        javaNativeCodeSandBox.executeCodeResponse(executeCodeRequest);
    }
}
