package top.enderherman.wetalk.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class AGlobalExceptionHandlerController extends ABaseController {

    private static final Logger logger = LoggerFactory.getLogger(AGlobalExceptionHandlerController.class);

    /**
     * 专门处理文件上传错误
     */
    @ExceptionHandler(MultipartException.class)
    public BaseResponse<?> handleMultipartException(MultipartException e, HttpServletRequest request) {
        logger.error("文件上传错误，请求地址{},错误信息:", request.getRequestURL(), e);
        BaseResponse<?> response = new BaseResponse<>();
        response.setCode(ResponseCodeEnum.CODE_600.getCode());
        response.setMessage("文件上传失败，请检查文件大小或格式");
        response.setStatus(STATUS_ERROR);
        return response;
    }

    @ExceptionHandler(value = Exception.class)
    Object handleException(Exception e, HttpServletRequest request) {
        logger.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        BaseResponse<?> ajaxResponse = new BaseResponse<>();
        //404
        if (e instanceof NoHandlerFoundException) {
            ajaxResponse.setCode(ResponseCodeEnum.CODE_404.getCode());
            ajaxResponse.setMessage(ResponseCodeEnum.CODE_404.getMsg());
            ajaxResponse.setStatus(STATUS_ERROR);
        } else if (e instanceof BusinessException) {
            //业务错误
            BusinessException biz = (BusinessException) e;
            ajaxResponse.setCode(biz.getCode() == null ? ResponseCodeEnum.CODE_600.getCode() : biz.getCode());
            ajaxResponse.setMessage(biz.getMessage());
            ajaxResponse.setStatus(STATUS_ERROR);
        } else if (e instanceof BindException || e instanceof MethodArgumentTypeMismatchException
                || e instanceof ConstraintViolationException || e instanceof HandlerMethodValidationException) {
            //参数类型错误
            ajaxResponse.setCode(ResponseCodeEnum.CODE_600.getCode());
            ajaxResponse.setMessage(ResponseCodeEnum.CODE_600.getMsg());
            ajaxResponse.setStatus(STATUS_ERROR);
        } else if (e instanceof DuplicateKeyException) {
            //主键冲突
            ajaxResponse.setCode(ResponseCodeEnum.CODE_601.getCode());
            ajaxResponse.setMessage(ResponseCodeEnum.CODE_601.getMsg());
            ajaxResponse.setStatus(STATUS_ERROR);
        } else {
            ajaxResponse.setCode(ResponseCodeEnum.CODE_500.getCode());
            ajaxResponse.setMessage(ResponseCodeEnum.CODE_500.getMsg());
            ajaxResponse.setStatus(STATUS_ERROR);
        }
        return ajaxResponse;
    }
}
