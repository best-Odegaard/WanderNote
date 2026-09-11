package com.gkv.dto;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "分页查询参数")
public class PageQuery {//分页查询请求·参数

    @ApiModelProperty(value = "当前页码，默认为1")
    private Long pageNum=1L;

    @ApiModelProperty(value = "每页数量，默认为10")
    private Long pageSize=10L;

    /*@ApiModelProperty(value = "排序字段")
    private String sortBy;*/

    @ApiModelProperty(value = "是否升序,默认升序true,降序:false")
    private Boolean isAsc=true;


    public <T> Page<T> toMpPage(OrderItem... orders){//接收多个初始化排序参数
        // 1.分页条件
        Page<T> p = Page.of(pageNum, pageSize);
        // 2.排序条件
        // 2.1.先看前端有没有传排序字段
        /*if (sortBy != null) {
            p.addOrder(new OrderItem());
            return p;
        }*/
        // 2.2.再看有没有手动指定排序字段
        if(orders != null){
            p.addOrder(orders);
        }
        return p;
    }

//懒汉编程
//      任意类型泛型T
    //调用者想调用但是不通过new对象，可以提前创建一个方法
    public <T> Page<T> toMpPage(String defaultSortBy, boolean isAsc){//需要排序参数
        //1.分页条件
        return this.toMpPage(new OrderItem());
    }

//  提前创建好根据创建时间来当默认排序
    public <T> Page<T> toMpPageDefaultSortByCreateTimeDesc() {
        return toMpPage("create_time", false);
    }

//   提前创建好根据更新时间来当默认排序
    public <T> Page<T> toMpPageDefaultSortByUpdateTimeDesc() {
        return toMpPage("update_time", false);
    }
}



