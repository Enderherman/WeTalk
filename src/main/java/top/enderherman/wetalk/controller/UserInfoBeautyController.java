package top.enderherman.wetalk.controller;


import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.enderherman.wetalk.annotation.GlobalInterceptor;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.entity.po.UserInfoBeauty;
import top.enderherman.wetalk.entity.query.UserInfoBeautyQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.service.UserInfoBeautyService;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;

@Validated
@RestController
@RequestMapping("/userInfoBeauty")
public class UserInfoBeautyController {

    @Resource
    private UserInfoBeautyService userInfoBeautyService;

    /**
     * 加载靓号列表
     */
    @PostMapping("/loadBeautyAccountList")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<PaginationResultVO<UserInfoBeauty>> loadBeautyAccountList(UserInfoBeautyQuery query) {
        query.setOrderBy("id desc");
        PaginationResultVO<UserInfoBeauty> resultVO = userInfoBeautyService.findListByPage(query);
        return BaseResponse.success(resultVO);
    }


    /**
     * 新增/修改靓号
     */
    @PostMapping("/saveBeautyAccount")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> saveBeautyAccount(UserInfoBeauty userInfoBeauty) {
        userInfoBeautyService.saveBeautyAccount(userInfoBeauty);
        return BaseResponse.success();
    }

    /**
     * 删除靓号
     */
    @PostMapping("/deleteBeautyAccount")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> deleteBeautyAccount(@NotNull Integer id) {
        userInfoBeautyService.deleteUserInfoBeautyById(id);
        return BaseResponse.success();
    }
}
