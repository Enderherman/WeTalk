package top.enderherman.wetalk.controller;

import com.wf.captcha.ArithmeticCaptcha;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.wetalk.annotation.GlobalInterceptor;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.component.RedisComponent;
import top.enderherman.wetalk.config.AppConfig;
import top.enderherman.wetalk.constants.Constants;
import top.enderherman.wetalk.entity.dto.TokenUserInfoDto;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.vo.UserInfoVO;
import top.enderherman.wetalk.entity.vo.WebSessionVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.service.UserInfoService;
import top.enderherman.wetalk.utils.CopyUtils;
import top.enderherman.wetalk.utils.RedisUtils;
import top.enderherman.wetalk.utils.StringUtils;
import top.enderherman.wetalk.utils.WebAuthCookie;
import top.enderherman.wetalk.webSocket.ChannelContextUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;


@Validated
@RestController
@RequestMapping("/account")
public class UserInfoController extends ABaseController {


    @Resource
    private RedisUtils<Object> redisUtils;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private ChannelContextUtils channelContextUtils;

    @Resource
    private AppConfig appConfig;


    /**
     * 获取验证码
     *
     * @return 验证码对应的UUID以及base64的验证码图片
     */
    @RequestMapping("/checkCode")
    public BaseResponse<?> checkCode() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(100, 42);
        String code = captcha.text();
        String checkCodeKey = UUID.randomUUID().toString();
        String checkCodeBase64 = captcha.toBase64();
        redisUtils.setEx(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey, code, Constants.REDIS_KEY_EXPIRES_TEN_MIN);

        HashMap<String, String> result = new HashMap<>();

        result.put(Constants.CHECK_CODE, checkCodeBase64);
        result.put(Constants.CHECK_CODE_KEY, checkCodeKey);
        return getSuccessResponseVO(result);
    }


    @PostMapping("/register")
    public BaseResponse<?> register(@NotNull String checkCodeKey,
                                    @NotNull String email,
                                    @NotNull @Pattern(regexp = Constants.REGEX_PASSWORD) String password,
                                    @NotNull String nickName,
                                    @NotNull String checkCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) redisUtils.get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey))) {
                throw new BusinessException("图片验证码错误");
            }

            userInfoService.register(email, nickName, password);
            return getSuccessResponseVO(null);
        } finally {
            redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
        }
    }

    @PostMapping("/login")
    public BaseResponse<?> login(@NotNull String checkCodeKey,
                                 @NotNull  String email,
                                 @NotNull String password,

                                 @NotNull String checkCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) redisUtils.get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey))) {
                throw new BusinessException("图片验证码错误");
            }
            UserInfoVO userInfoVO = userInfoService.login(email, password);
            return getSuccessResponseVO(userInfoVO);
        } finally {
            redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
        }
    }

    /**
     * Web login stores the session token in an HttpOnly cookie and omits it from JSON.
     */
    @PostMapping("/webLogin")
    public BaseResponse<WebSessionVO> webLogin(HttpServletResponse response,
                                               @NotNull String checkCodeKey,
                                               @NotNull String email,
                                               @NotNull String password,
                                               @NotNull String checkCode) {
        try {
            validateCaptcha(checkCodeKey, checkCode);
            UserInfoVO userInfo = userInfoService.login(email, password);
            response.addHeader(HttpHeaders.SET_COOKIE,
                    WebAuthCookie.session(userInfo.getToken(), appConfig.isWebAuthCookieSecure()).toString());
            return getSuccessResponseVO(CopyUtils.copy(userInfo, WebSessionVO.class));
        } finally {
            redisUtils.delete(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey);
        }
    }

    @PostMapping("/webSocketTicket")
    @GlobalInterceptor
    public BaseResponse<?> createWebSocketTicket(HttpServletRequest request) {
        TokenUserInfoDto user = getTokenUserDto(request);
        String ticket = UUID.randomUUID().toString();
        redisComponent.saveWebSocketTicket(ticket, user);
        return getSuccessResponseVO(java.util.Map.of("ticket", ticket));
    }

    /**
     * 获取系统配置
     */
    @PostMapping("/getSysSetting")
    @GlobalInterceptor
    public BaseResponse<?> getSysSetting() {
        return getSuccessResponseVO(redisComponent.getSysSetting());
    }

    /**
     * 获取用户信息
     */
    @RequestMapping("/getUserInfo")
    @GlobalInterceptor
    public BaseResponse<?> getUserInfo(HttpServletRequest request) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        UserInfo userInfo = userInfoService.getUserInfoByUserId(tokenUserInfoDto.getUserId());
        UserInfoVO userInfoVO = CopyUtils.copy(userInfo, UserInfoVO.class);
        userInfoVO.setAdmin(tokenUserInfoDto.isAdmin());
        return getSuccessResponseVO(userInfoVO);
    }

    /**
     * 修改用户信息
     */
    @RequestMapping("/saveUserInfo")
    @GlobalInterceptor
    public BaseResponse<?> saveUserInfo(HttpServletRequest request,
                                        UserInfo userInfo,
                                        MultipartFile avatarFile,
                                        MultipartFile coverFile) throws IOException {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        userInfo.setUserId(tokenUserInfoDto.getUserId());
        userInfoService.updateUserInfo(userInfo, avatarFile, coverFile);
        return getUserInfo(request);
    }

    /**
     * 修改密码
     */
    @RequestMapping("/updatePassword")
    @GlobalInterceptor
    public BaseResponse<?> updatePassword(HttpServletRequest request,
                                          HttpServletResponse response,
                                          @NotNull @Pattern(regexp = Constants.REGEX_PASSWORD) String password) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(StringUtils.encodingByMd5(password));
        userInfoService.updateUserInfoByUserId(userInfo, tokenUserInfoDto.getUserId());
        //重新登陆 强制退出
        channelContextUtils.closeContact(tokenUserInfoDto.getUserId());
        response.addHeader(HttpHeaders.SET_COOKIE, WebAuthCookie.clear(appConfig.isWebAuthCookieSecure()).toString());
        return getSuccessResponseVO(null);
    }

    /**
     * 退出登录
     */
    @RequestMapping("/logout")
    @GlobalInterceptor
    public BaseResponse<?> updatePassword(HttpServletRequest request, HttpServletResponse response) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserDto(request);
        channelContextUtils.closeContact(tokenUserInfoDto.getUserId());
        response.addHeader(HttpHeaders.SET_COOKIE, WebAuthCookie.clear(appConfig.isWebAuthCookieSecure()).toString());
        return getSuccessResponseVO(null);
    }

    private void validateCaptcha(String checkCodeKey, String checkCode) {
        if (!checkCode.equalsIgnoreCase((String) redisUtils.get(Constants.REDIS_KEY_CHECK_CODE + checkCodeKey))) {
            throw new BusinessException("图片验证码错误");
        }
    }
}
