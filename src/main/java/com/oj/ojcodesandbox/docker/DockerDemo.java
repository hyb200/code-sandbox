package com.oj.ojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;

/**
 * Docker实例demo
 */
public class DockerDemo {
    public static void main(String[] args) throws InterruptedException {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        //  拉取镜像
//        String image = "nginx:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("拉取镜像中：" + item.getStatus());
//                super.onNext(item);
//            }
//        };
//        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
//        System.out.println("拉取镜像成功");

        // 创建容器
        String image = "hello-world";
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse containerResponse = containerCmd.exec();
        System.out.println(containerResponse);
        //  获取容器的ID
        String containerId = containerResponse.getId();

        //  查看容器状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containers = listContainersCmd.withShowAll(true).exec();
        for (Container container : containers) {
            System.out.println(container);
        }

        //  启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //  查看容器日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println("日志：" + item.toString());
                super.onNext(item);
            }
        };
        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();

        //  删除容器
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        //  删除镜像
        dockerClient.removeImageCmd(image).exec();
    }
}
