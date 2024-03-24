package com.oj.ojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.oj.ojcodesandbox.model.ExecuteCodeRequest;
import com.oj.ojcodesandbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PythonDockerCodeSandbox extends CodeSandboxTemplate {

    public static final long TIME_OUT = 1000L;

    public static final boolean FIRST_INIT = false;

    //  获取默认的 Docker Client
    private static DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    private static String containerId;

    /**
     * 判题初始化，保存代码和输入用例为文件
     * @param code 用户代码
     * @return 代码文件
     */
    @Override
    public File judgeInit(String code, List<String> inputs) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //  判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        //  将用户的代码隔离存放
        String judgeDirPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = judgeDirPath + File.separator + "a.py";
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        for (int i = 0; i < inputs.size(); i ++) {
            String inputFilePath = judgeDirPath + File.separator + i + suffix;
            FileUtil.writeString(inputs.get(i), inputFilePath, StandardCharsets.UTF_8);
        }

        return userCodeFile;
    }

    /**
     * 初始化容器，挂载文件容器卷
     *
     * @param file
     */
    public void initContainer(File file) {
        String image = "python:3.8.18-slim-bullseye";

        //  拉取镜像
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }

        //  创建容器，并将文件复制到容器内
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(file.getParent(), new Volume("/code")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)  //  关闭网络
                .withReadonlyRootfs(true)
                .withWorkingDir("/code")
                .withStdinOpen(true)
                .withStdInOnce(true)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        //  获取容器ID
        containerId = createContainerResponse.getId();

        //  启动容器
        dockerClient.startContainerCmd(containerId).exec();
    }

    /**
     * 编译代码，python无需编译，直接返回编译成功
     *
     * @param userCodeFile 用户代码文件
     * @return
     */
    @Override
    public ExecuteMessage compileFile(File userCodeFile) {
        initContainer(userCodeFile);
        ExecuteMessage executeMessage = new ExecuteMessage();
        executeMessage.setExitValue(0);
        return executeMessage;
    }


    @Override
    public List<ExecuteMessage> runCode(File userCodeFile, List<String> inputs) {
        //  执行交互式程序
        StopWatch stopWatch = new StopWatch();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i ++ ) {
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", String.format("cat /code/%d.in | python3 /code/a.py", i))
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();


            String execId = execCreateCmdResponse.getId();

            ExecuteMessage executeMessage = new ExecuteMessage();
            executeMessage.setExitValue(0);
            final String[] errorMsg = {null};
            final String[] msg = {null};

            final boolean[] timeout = {true};
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    //  如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                    executeMessage.setExitValue(0);
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMsg[0] = new String(frame.getPayload());
                    } else {
                        msg[0] = StringUtils.removeEnd(new String(frame.getPayload()), "\n") ;
                    }

                    super.onNext(frame);
                }
            };

            //  获取容器占用的内存
            final long[] memory = {0l};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> resultCallback = new ResultCallback<Statistics>() {

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    memory[0] = Math.max(statistics.getMemoryStats().getUsage(), memory[0]);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            };
            statsCmd.exec(resultCallback);

            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                Thread.sleep(TIME_OUT); //  线程休眠以获取容器内存占用
                statsCmd.close();
            } catch (Exception e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            executeMessage.setMessage(msg[0]);
            executeMessage.setErrorMessage(errorMsg[0]);
            if (errorMsg[0] != null) executeMessage.setExitValue(1);
            executeMessage.setMemory(memory[0]);
            executeMessageList.add(executeMessage);
        }

        stopAndRemoveContainer(containerId);
        return executeMessageList;
    }

    /**
     * 停止、删除容器
     *
     * @param containerId
     */
    public void stopAndRemoveContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
    }

    public static void main(String[] args) {
        PythonDockerCodeSandbox javaNativeCodeSandBox = new PythonDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputs(Arrays.asList("1\n 2\n", "1\n 3\n"));

        String code = ResourceUtil.readStr("test/a.py", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("python");
        javaNativeCodeSandBox.executeCodeResponse(executeCodeRequest);
    }
}
