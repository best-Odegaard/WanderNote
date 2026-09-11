package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.constant.JwtClaimsConstant;
import com.gkv.constant.MessageConstant;
import com.gkv.constant.StatusConstant;
import com.gkv.context.BaseContext;
import com.gkv.dto.UserInfoDTO;
import com.gkv.dto.UserLoginDTO;
import com.gkv.dto.UserRegisterDTO;
import com.gkv.entity.User;
import com.gkv.exception.AccountLockedException;
import com.gkv.exception.AccountNotFoundException;
import com.gkv.exception.BaseException;
import com.gkv.exception.PasswordErrorException;
import com.gkv.mapper.UserMapper;
import com.gkv.properties.JwtProperties;
import com.gkv.service.UserService;
import com.gkv.utils.JwtUtil;
import com.gkv.vo.UserInfoVO;
import com.gkv.vo.UserLoginVO;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public void register(UserRegisterDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User existUser = userMapper.selectOne(wrapper);
        //判断是否存在相同的用户名
        if (existUser != null) {
            throw new BaseException("用户名已存在");
        }

        //判断是否存在相同的手机号
        if (StringUtils.isNotBlank(dto.getPhone())) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, dto.getPhone());
            User existPhoneUser = userMapper.selectOne(phoneWrapper);
            if (existPhoneUser != null) {
                throw new BaseException("该手机号已注册");
            }
        }

        //将数据存入user对象（空串归一化为null，避免 phone/email 唯一索引冲突）
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setPhone(StringUtils.isNotBlank(dto.getPhone()) ? dto.getPhone() : null);
        user.setEmail(StringUtils.isNotBlank(dto.getEmail()) ? dto.getEmail() : null);
        user.setNickname(StringUtils.isNotBlank(dto.getNickname()) ? dto.getNickname() : null);
        user.setPoints(0);
        user.setStatus(StatusConstant.ENABLE);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        //存进数据库
        userMapper.insert(user);
        log.info("用户注册成功：{}", dto.getUsername());
    }


    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User user = userMapper.selectOne(wrapper);
        //判断账户是否存在
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //判断账户有没有被封
        if (user.getStatus() == StatusConstant.DISABLE) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //验证密码
        if (!dto.getPassword().equals(user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        //生成通行证
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        //返回数据给前端
        UserLoginVO vo = new UserLoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setToken(token);
        return vo;
    }

    @Override
    public UserInfoVO getInfo() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        return toInfoVO(user);
    }

    @Override
    public UserInfoVO updateInfo(UserInfoDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }

        // 只更新允许的字段，避免误改 username/password
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getBirthday() != null) user.setBirthday(dto.getBirthday());
        if (dto.getBio() != null) user.setBio(dto.getBio());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.updateById(user);
        log.info("用户{}更新资料成功", userId);
        return toInfoVO(user);
    }

    private UserInfoVO toInfoVO(User user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday());
        vo.setBio(user.getBio());
        return vo;
    }
}
