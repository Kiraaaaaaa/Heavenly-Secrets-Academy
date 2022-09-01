package com.tianji.user.domain.dto;

import com.tianji.api.dto.user.UserDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "修改用户信息的表单，带有密码")
public class UserFormDTO extends UserDTO {
    @ApiModelProperty(value = "原始密码", example = "123321")
    private String oldPassword;
    @ApiModelProperty(value = "新密码", example = "123321")
    private String password;
}
