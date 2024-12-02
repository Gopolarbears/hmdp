package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * ClassName: IFollowService
 * Description:
 *
 * @Author Seth Neiman
 * @Create 2024/5/22 10:58
 * @Version 1.0
 */
public interface IFollowService extends IService<Follow> {
    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
