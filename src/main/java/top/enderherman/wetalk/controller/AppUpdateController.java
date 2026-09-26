package top.enderherman.wetalk.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.annotation.GlobalInterceptor;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.AppUpdate;
import top.enderherman.wetalk.entity.query.AppUpdateQuery;
import top.enderherman.wetalk.entity.vo.AppUpdateVO;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.service.AppUpdateService;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Validated
@RestController("appUpdateController")
@RequestMapping("/app")
public class AppUpdateController extends ABaseController {

    @Resource
    private AppUpdateService appUpdateService;

    /**
     * 获取更新信息列表
     */
    @RequestMapping("/loadUpdateList")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<PaginationResultVO<AppUpdate>> loadUpdateList(AppUpdateQuery query) {
        query.setOrderBy("id desc");
        PaginationResultVO<AppUpdate> resultVO = appUpdateService.findListByPage(query);
        return BaseResponse.success(resultVO);
    }

    /**
     * 发布或者修改更新
     */
    @RequestMapping("/saveUpdate")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> saveUpdate(Integer id,
                                      @NotNull String version,
                                      @NotNull String updateDesc,
                                      @NotNull Integer fileType,
                                      String outerLink,
                                      MultipartFile file) throws IOException {
        AppUpdate appUpdate = new AppUpdate();
        appUpdate.setId(id);
        appUpdate.setVersion(version);
        appUpdate.setUpdateDesc(updateDesc);
        appUpdate.setFileType(fileType);
        appUpdate.setOuterLink(outerLink==null?"":outerLink);
        appUpdateService.saveUpdate(appUpdate, file);
        return BaseResponse.success();
    }

    /**
     * 删除更新
     */
    @RequestMapping("/deleteUpdate")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> deleteUpdate(@NotNull Integer id) {
        appUpdateService.deleteAppUpdateById(id);
        return BaseResponse.success();
    }

    /**
     * 发布更新
     */
    @RequestMapping("/postUpdate")
    @GlobalInterceptor(checkAdmin = true)
    public BaseResponse<?> postUpdate(@NotNull Integer id, @NotNull Integer status, String grayscaleUid) {
        appUpdateService.postUpdate(id, status, grayscaleUid);
        return BaseResponse.success();
    }

    /**
     * 检测更新
     */
    @RequestMapping("/checkUpdate")
    @GlobalInterceptor
    public BaseResponse<AppUpdateVO> checkUpdate(String version, String uid) {
        return StringUtils.isEmpty(version) ? BaseResponse.success() : BaseResponse.success(appUpdateService.getLatestUpdate(version, uid));
    }

    /**
     * 下载已发布且对当前账号可见的本地更新包
     */
    @PostMapping("/downloadUpdate")
    @GlobalInterceptor
    public void downloadUpdate(HttpServletRequest request,
                               HttpServletResponse response,
                               @NotNull Integer id) {
        TokenUserInfoDto user = getTokenUserDto(request);
        File file = appUpdateService.getDownloadFile(id, user.getUserId());
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment");
        response.setContentLengthLong(file.length());
        try (FileInputStream input = new FileInputStream(file)) {
            input.transferTo(response.getOutputStream());
        } catch (IOException error) {
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }
}
