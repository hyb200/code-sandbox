package com.oj.ojcodesandbox.security;

import java.security.Permission;

public class DefaultSecurityManager extends SecurityManager{
    @Override
    public void checkPermission(Permission perm) {
        System.out.println("暂时不做任何限制");
        super.checkPermission(perm);
    }
}
