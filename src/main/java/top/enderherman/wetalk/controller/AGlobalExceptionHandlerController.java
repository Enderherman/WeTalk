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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.UUID;

@RestControllerAdvice
public class AGlobalExceptionHandlerController extends ABaseController {

    private static final Logger logger = LoggerFactory.getLogger(AGlobalExceptionHandlerController.class);

    /**
     * 专门处理文件上传错误
     */
    @ExceptionHandler(MultipartException.class)
    public BaseResponse<?> handleMultipartException(MultipartException e, HttpServletRequest request,
                                                    HttpServletResponse servletResponse) {
        String requestId = attachRequestId(servletResponse);
        logger.warn("Multipart request rejected requestId={} method={} path={} type={}", requestId,
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        BaseResponse<?> response = new BaseResponse<>();
        response.setCode(ResponseCodeEnum.CODE_600.getCode());
        response.setMessage("文件上传失败，请检查文件大小或格式");
        response.setStatus(STATUS_ERROR);
        return response;
    }

    @ExceptionHandler(value = Exception.class)
    Object handleException(Exception e, HttpServletRequest request, HttpServletResponse servletResponse) {
        String requestId = attachRequestId(servletResponse);
        logFailure(e, request, requestId);
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

    private String attachRequestId(HttpServletResponse response) {
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        return requestId;
    }

    private void logFailure(Exception error, HttpServletRequest request, String requestId) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (error instanceof BusinessException business) {
            logger.warn("Business request rejected requestId={} method={} path={} code={}", requestId,
                    method, path, business.getCode());
        } else if (error instanceof BindException || error instanceof MethodArgumentTypeMismatchException
                || error instanceof ConstraintViolationException || error instanceof HandlerMethodValidationException) {
            logger.warn("Request validation failed requestId={} method={} path={} type={}", requestId,
                    method, path, error.getClass().getSimpleName());
        } else if (error instanceof DuplicateKeyException) {
            logger.warn("Duplicate request data requestId={} method={} path={}", requestId, method, path);
        } else if (error instanceof NoHandlerFoundException) {
            logger.info("Unknown request path requestId={} method={} path={}", requestId, method, path);
        } else {
            // Do not log exception messages or stacks that may contain SQL, paths, or submitted values.
            logger.error("Unhandled request failure requestId={} method={} path={} type={}", requestId,
                    method, path, error.getClass().getSimpleName());
        }
    }
}
