package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.constant.StatusConstant;
import com.gkv.dto.AdminUserPageDTO;
import com.gkv.entity.User;
import com.gkv.exception.BaseException;
import com.gkv.mapper.UserMapper;
import com.gkv.result.PageResult;
import com.gkv.service.AdminUserService;
import com.gkv.vo.AdminUserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<AdminUserVO> page(AdminUserPageDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, User::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(User::getUsername, dto.getKeyword())
                        .or()
                        .like(User::getNickname, dto.getKeyword())
                        .or()
                        .like(User::getPhone, dto.getKeyword()));

        Page<User> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        page.addOrder(OrderItem.desc("create_time"));

        Page<User> result = userMapper.selectPage(page, wrapper);
        List<AdminUserVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public AdminUserVO detail(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        log.info("管理员{}修改用户{}状态为{}", com.gkv.context.BaseContext.getCurrentId(), id, status);
    }

    /** 实体转 VO（不含密码） */
    private AdminUserVO toVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
