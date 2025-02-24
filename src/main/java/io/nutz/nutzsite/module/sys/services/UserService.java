package io.nutz.nutzsite.module.sys.services;

import io.nutz.nutzsite.common.base.Service;
import io.nutz.nutzsite.common.utils.ShiroUtils;
import io.nutz.nutzsite.module.sys.models.Role;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.nutz.boot.starter.caffeine.Cache;
import org.nutz.dao.Cnd;
import org.nutz.dao.Dao;
import org.nutz.dao.sql.Criteria;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import io.nutz.nutzsite.module.sys.models.User;
import org.nutz.lang.Lang;
import org.nutz.lang.Strings;

import java.util.*;

/**
 * 用户 服务层实现
 *
 * @author haiming
 * @date 2019-04-12
 */
@IocBean(args = {"refer:dao"})
public class UserService extends Service<User> {
    public UserService(Dao dao) {
        super(dao);
    }

    @Inject
    private RoleService roleService;
    @Inject
    private MenuService menuService;


    /**
     * 新增
     *
     * @param user
     * @return
     */
    @Override
    public User insert(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        String salt = rng.nextBytes().toBase64();
        user.setSalt(salt);
        String hashedPasswordBase64 = new Sha256Hash(user.getPassword(), salt, 1024).toBase64();
        user.setPassword(hashedPasswordBase64);
        List<String> ids = new ArrayList<>();
        if (user != null && user.getRoleIds() != null) {
            if (Strings.isNotBlank(user.getRoleIds())) {
                ids = Arrays.asList(user.getRoleIds().split(","));
            }
        }
        dao().insert(user);
        this.updataRelation(user);
        return user;
    }

    /**
     * 更新
     *
     * @param data
     * @return
     */
    public int update(User data) {
        //忽略空字段
        int count = dao().updateIgnoreNull(data);
        this.updataRelation(data);
        return count;
    }

    /**
     * 更新角色
     *
     * @param data
     */
    public void updataRelation(User data) {
        List<String> ids = new ArrayList<>();
        if (data != null && Strings.isNotBlank(data.getRoleIds())) {
            if (Strings.isNotBlank(data.getRoleIds())) {
                ids = Arrays.asList(data.getRoleIds().split(","));
            }
            //清除已有关系
            User tmpData = this.fetch(data.getId());
            this.fetchLinks(tmpData, "roles");
            dao().clearLinks(tmpData, "roles");
        }
        if (ids != null && ids.size() > 0) {
            Criteria cri = Cnd.cri();
            cri.where().andInStrList("id", ids);
            List<Role> roleList = roleService.query(cri);
            data.setRoles(roleList);
        }
        //更新关系
        dao().insertRelation(data, "roles");
    }


    /**
     * 重置密码
     *
     * @param user
     * @return
     */
    public int resetUserPwd(User user) {
        RandomNumberGenerator rng = new SecureRandomNumberGenerator();
        String salt = rng.nextBytes().toBase64();
        user.setSalt(salt);
        String hashedPasswordBase64 = new Sha256Hash(user.getPassword(), salt, 1024).toBase64();
        user.setPassword(hashedPasswordBase64);
        user.setUpdateTime(new Date());
        return dao().updateIgnoreNull(user);
    }

    /**
     * 获取角色列表
     *
     * @param user
     * @return
     */
    @Cache
    public Set<String> getRoleCodeList(User user) {
        this.fetchLinks(user, "roles");
        Set<String> permsSet = new HashSet<>();
        for (Role role : user.getRoles()) {
            if (!role.isStatus() && !role.isDelFlag()) {
                permsSet.add(role.getRoleKey());
            }
        }
        return permsSet;
    }

    /**
     * 获取用户权限
     *
     * @param userId
     * @return
     */
    @Cache
    public Set<String> getPermsByUserId(String userId) {
        Set<String> permsSet = new HashSet<>();
        List<String> menuList = menuService.getPermsByUserId(userId);
        menuList.forEach(menu -> {
            if (Strings.isNotBlank(menu)) {
                permsSet.add(menu);
            }

        });
        return permsSet;
    }

    /**
     * 记录登录信息
     */
    public void recordLoginInfo(User user) {
        user.setLoginIp(ShiroUtils.getIp());
        user.setLoginDate(new Date());
        dao().updateIgnoreNull(user);
    }

    /**
     * 查询用户所属角色组
     *
     * @param userId
     * @return
     */
    public String getUserRoleGroup(String userId) {
        User user = this.fetch(userId);
        user = this.fetchLinks(user, "roles");
        StringBuffer idsStr = new StringBuffer();
        for (Role role : user.getRoles()) {
            idsStr.append(role.getRoleName()).append(",");
        }
        if (Strings.isNotBlank(idsStr.toString())) {
            return idsStr.substring(0, idsStr.length() - 1);
        }
        return idsStr.toString();
    }

    public boolean checkLoginNameUnique(String name) {
        List<User> list = this.query(Cnd.where("login_name", "=", name));
        if (Lang.isEmpty(list)) {
            return true;
        }
        return false;
    }

}
