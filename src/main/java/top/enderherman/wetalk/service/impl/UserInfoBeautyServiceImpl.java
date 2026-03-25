package top.enderherman.wetalk.service.impl;

import org.springframework.stereotype.Service;
import top.enderherman.wetalk.common.ResponseCodeEnum;
import top.enderherman.wetalk.entity.enums.BeautyAccountStatusEnum;
import top.enderherman.wetalk.entity.enums.PageSize;
import top.enderherman.wetalk.entity.po.UserInfo;
import top.enderherman.wetalk.entity.po.UserInfoBeauty;
import top.enderherman.wetalk.entity.query.SimplePage;
import top.enderherman.wetalk.entity.query.UserInfoBeautyQuery;
import top.enderherman.wetalk.entity.vo.PaginationResultVO;
import top.enderherman.wetalk.exception.BusinessException;
import top.enderherman.wetalk.mappers.UserInfoBeautyMapper;
import top.enderherman.wetalk.mappers.UserInfoMapper;
import top.enderherman.wetalk.service.UserInfoBeautyService;
import top.enderherman.wetalk.utils.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;


/**
 * 靓号表 业务接口实现
 */
@Service("userInfoBeautyService")
public class UserInfoBeautyServiceImpl implements UserInfoBeautyService {

    @Resource
    private UserInfoBeautyMapper<UserInfoBeauty, UserInfoBeautyQuery> userInfoBeautyMapper;

    @Resource
    private UserInfoMapper<UserInfo, UserInfoBeauty> userInfoMapper;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<UserInfoBeauty> findListByParam(UserInfoBeautyQuery param) {
        return this.userInfoBeautyMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserInfoBeautyQuery param) {
        return this.userInfoBeautyMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<UserInfoBeauty> findListByPage(UserInfoBeautyQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserInfoBeauty> list = this.findListByParam(param);
        PaginationResultVO<UserInfoBeauty> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return result;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(UserInfoBeauty bean) {
        return this.userInfoBeautyMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<UserInfoBeauty> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userInfoBeautyMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<UserInfoBeauty> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userInfoBeautyMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(UserInfoBeauty bean, UserInfoBeautyQuery param) {
        StringUtils.checkParam(param);
        return this.userInfoBeautyMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(UserInfoBeautyQuery param) {
        StringUtils.checkParam(param);
        return this.userInfoBeautyMapper.deleteByParam(param);
    }

    /**
     * 根据Id获取对象
     */
    @Override
    public UserInfoBeauty getUserInfoBeautyById(Integer id) {
        return this.userInfoBeautyMapper.selectById(id);
    }

    /**
     * 根据Id修改
     */
    @Override
    public Integer updateUserInfoBeautyById(UserInfoBeauty bean, Integer id) {
        return this.userInfoBeautyMapper.updateById(bean, id);
    }

    /**
     * 根据Id删除
     */
    @Override
    public Integer deleteUserInfoBeautyById(Integer id) {
        return this.userInfoBeautyMapper.deleteById(id);
    }

    /**
     * 根据UserId获取对象
     */
    @Override
    public UserInfoBeauty getUserInfoBeautyByUserId(String userId) {
        return this.userInfoBeautyMapper.selectByUserId(userId);
    }

    /**
     * 根据UserId修改
     */
    @Override
    public Integer updateUserInfoBeautyByUserId(UserInfoBeauty bean, String userId) {
        return this.userInfoBeautyMapper.updateByUserId(bean, userId);
    }

    /**
     * 根据UserId删除
     */
    @Override
    public Integer deleteUserInfoBeautyByUserId(String userId) {
        return this.userInfoBeautyMapper.deleteByUserId(userId);
    }

    /**
     * 根据Email获取对象
     */
    @Override
    public UserInfoBeauty getUserInfoBeautyByEmail(String email) {
        return this.userInfoBeautyMapper.selectByEmail(email);
    }

    /**
     * 根据Email修改
     */
    @Override
    public Integer updateUserInfoBeautyByEmail(UserInfoBeauty bean, String email) {
        return this.userInfoBeautyMapper.updateByEmail(bean, email);
    }

    /**
     * 根据Email删除
     */
    @Override
    public Integer deleteUserInfoBeautyByEmail(String email) {
        return this.userInfoBeautyMapper.deleteByEmail(email);
    }

    /**
     * 新增/更新靓号信息
     */
    @Override
    public void saveBeautyAccount(UserInfoBeauty beauty) {
        //1.如果是修改操作，首先根据ID查询记录，并判断状态是否为已使用
        if (beauty.getId() != null) {
            UserInfoBeauty existingById = userInfoBeautyMapper.selectById(beauty.getId());
            if (BeautyAccountStatusEnum.USEED.getStatus().equals(existingById.getStatus())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }
        //2.校验靓号邮箱的唯一性
        UserInfoBeauty existingByEmail = userInfoBeautyMapper.selectByEmail(beauty.getEmail());
        if (beauty.getId() == null) {
            // 新增时，如果邮箱已存在，则抛出异常
            if (existingByEmail != null) {
                throw new BusinessException("靓号邮箱已经存在");
            }
        } else {
            // 修改时，如果查询到的记录不等于当前记录，则邮箱冲突
            if (existingByEmail != null && !beauty.getId().equals(existingByEmail.getId())) {
                throw new BusinessException("靓号邮箱已经存在");
            }
        }
        // 3.校验靓号（通过userId）的唯一性
        UserInfoBeauty existingByUserId = userInfoBeautyMapper.selectByUserId(beauty.getUserId());
        if (beauty.getId() == null) {
            // 新增时，如果该用户已存在靓号，则抛出异常
            if (existingByUserId != null) {
                throw new BusinessException("靓号已存在");
            }
        } else {
            // 修改时，如果查询到的记录不等于当前记录，则说明靓号已被其他记录使用
            if (existingByUserId != null && !beauty.getId().equals(existingByUserId.getId())) {
                throw new BusinessException("靓号已存在");
            }
        }
        // 4.校验邮箱是否已在系统中注册（user_info表）
        UserInfo existingUserInfoByEmail = userInfoMapper.selectByEmail(beauty.getEmail());
        if (existingUserInfoByEmail != null) {
            throw new BusinessException("靓号邮箱已被注册");
        }
        // 5.校验用户是否已在系统中注册（user_info表）
        UserInfo existingUserInfoByUserId = userInfoMapper.selectByUserId(beauty.getUserId());
        if (existingUserInfoByUserId != null) {
            throw new BusinessException("靓号已经被注册");
        }
        // 6.根据是否有ID决定是更新还是新增
        if (beauty.getId() != null) {
            userInfoBeautyMapper.updateById(beauty, beauty.getId());
        } else {
            beauty.setStatus(BeautyAccountStatusEnum.NO_USE.getStatus());
            userInfoBeautyMapper.insert(beauty);
        }
    }

}