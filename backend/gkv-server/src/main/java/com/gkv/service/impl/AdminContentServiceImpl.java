package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.constant.StatusConstant;
import com.gkv.dto.ActivitySaveDTO;
import com.gkv.dto.AdminContentPageDTO;
import com.gkv.dto.ScenicSaveDTO;
import com.gkv.entity.ActivityInfo;
import com.gkv.entity.ScenicSpot;
import com.gkv.exception.BaseException;
import com.gkv.mapper.ActivityInfoMapper;
import com.gkv.mapper.ScenicSpotMapper;
import com.gkv.result.PageResult;
import com.gkv.service.AdminContentService;
import com.gkv.vo.ImportErrorVO;
import com.gkv.vo.ImportResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminContentServiceImpl implements AdminContentService {

    /** 景点导入模板列（与 parseRow 的列序一一对应） */
    private static final String[] SCENIC_TEMPLATE_HEADERS = {
            "名称*", "城市", "分类", "评分", "价格", "开放时间", "地址", "简介",
            "封面图URL", "图片URL(多个用|分隔)", "热门(是/否)", "排序", "状态(启用/禁用)"
    };

    /** 模板示例行 */
    private static final String[] SCENIC_TEMPLATE_EXAMPLE = {
            "鼎湖山", "肇庆", "自然风光", "4.8", "70", "08:00-18:00", "肇庆市鼎湖区",
            "广东四大名山之一", "https://example.com/cover.jpg",
            "https://example.com/a.jpg|https://example.com/b.jpg", "是", "1", "启用"
    };

    @Autowired
    private ScenicSpotMapper scenicSpotMapper;

    @Autowired
    private ActivityInfoMapper activityInfoMapper;

    // ─────────── 景点 ───────────

    @Override
    public PageResult<ScenicSpot> scenicPage(AdminContentPageDTO dto) {
        LambdaQueryWrapper<ScenicSpot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, ScenicSpot::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getCity()), ScenicSpot::getCity, dto.getCity())
                .eq(StringUtils.hasText(dto.getCategory()), ScenicSpot::getCategory, dto.getCategory())
                .like(StringUtils.hasText(dto.getKeyword()), ScenicSpot::getName, dto.getKeyword())
                .orderByAsc(ScenicSpot::getSortOrder);

        Page<ScenicSpot> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<ScenicSpot> result = scenicSpotMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public void scenicSave(ScenicSaveDTO dto) {
        if (dto.getId() == null) {
            ScenicSpot spot = new ScenicSpot();
            BeanUtils.copyProperties(dto, spot);
            spot.setImages(dto.getImages() != null ? JSON.toJSONString(dto.getImages()) : null);
            if (spot.getStatus() == null) spot.setStatus(StatusConstant.ENABLE);
            if (spot.getSortOrder() == null) spot.setSortOrder(0);
            if (spot.getIsHot() == null) spot.setIsHot(0);
            spot.setViewCount(0);
            spot.setCreateTime(LocalDateTime.now());
            spot.setUpdateTime(LocalDateTime.now());
            scenicSpotMapper.insert(spot);
            log.info("管理员新增景点：{}", spot.getName());
        } else {
            ScenicSpot spot = scenicSpotMapper.selectById(dto.getId());
            if (spot == null) {
                throw new BaseException("景点不存在");
            }
            BeanUtils.copyProperties(dto, spot);
            spot.setImages(dto.getImages() != null ? JSON.toJSONString(dto.getImages()) : null);
            spot.setUpdateTime(LocalDateTime.now());
            scenicSpotMapper.updateById(spot);
            log.info("管理员编辑景点：{}", spot.getName());
        }
    }

    @Override
    public void scenicDelete(Long id) {
        ScenicSpot spot = scenicSpotMapper.selectById(id);
        if (spot == null) {
            throw new BaseException("景点不存在");
        }
        scenicSpotMapper.deleteById(id);
        log.info("管理员删除景点：{}", spot.getName());
    }

    @Override
    public void scenicStatus(Long id, Integer status) {
        ScenicSpot spot = scenicSpotMapper.selectById(id);
        if (spot == null) {
            throw new BaseException("景点不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        ScenicSpot update = new ScenicSpot();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        scenicSpotMapper.updateById(update);
    }

    // ─────────── 景点 Excel 批量导入 ───────────

    @Override
    public ImportResultVO importScenic(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new BaseException("文件为空，请重新上传");
        }
        if (filename == null || !(filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"))) {
            throw new BaseException("仅支持 .xlsx / .xls 格式的 Excel 文件");
        }

        ImportResultVO result = new ImportResultVO();
        List<ImportErrorVO> errors = new ArrayList<>();
        int total = 0;
        int success = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BaseException("Excel 中没有工作表");
            }

            // 表头校验：第一行必须包含「名称」列
            Row header = sheet.getRow(0);
            boolean hasNameCol = false;
            if (header != null) {
                for (Cell cell : header) {
                    if ("名称*".equals(getCellValue(cell).trim()) || "名称".equals(getCellValue(cell).trim())) {
                        hasNameCol = true;
                        break;
                    }
                }
            }
            if (!hasNameCol) {
                throw new BaseException("Excel 格式不正确：第一行表头必须包含「名称*」列，请先下载模板");
            }

            // 逐行解析导入（错误行跳过，不中断）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                total++;
                try {
                    scenicSpotMapper.insert(parseScenicRow(row));
                    success++;
                } catch (Exception e) {
                    errors.add(new ImportErrorVO(i + 1, e.getMessage()));
                    log.warn("景点导入第{}行失败：{}", i + 1, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BaseException("Excel 解析失败：" + e.getMessage());
        }

        result.setTotal(total);
        result.setSuccess(success);
        result.setFail(errors.size());
        result.setErrors(errors);
        log.info("管理员{}批量导入景点：共{}行，成功{}，失败{}",
                com.gkv.context.BaseContext.getCurrentId(), total, success, errors.size());
        return result;
    }

    @Override
    public void scenicTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("景点导入模板");

            // 表头样式：加粗 + 浅蓝背景 + 边框
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);

            // 表头行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < SCENIC_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(SCENIC_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // 示例行
            Row exampleRow = sheet.createRow(1);
            for (int i = 0; i < SCENIC_TEMPLATE_EXAMPLE.length; i++) {
                Cell cell = exampleRow.createCell(i);
                cell.setCellValue(SCENIC_TEMPLATE_EXAMPLE[i]);
                cell.setCellStyle(bodyStyle);
            }

            // 列宽
            int[] widths = {18, 10, 12, 8, 8, 14, 24, 40, 32, 40, 12, 8, 12};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            // 写入响应
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String encoded = URLEncoder.encode("景点导入模板.xlsx", StandardCharsets.UTF_8.toString());
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            workbook.write(response.getOutputStream());
        } catch (IOException e) {
            throw new BaseException("模板生成失败：" + e.getMessage());
        }
    }

    /** 解析一行景点数据（列序与 SCENIC_TEMPLATE_HEADERS 对应） */
    private ScenicSpot parseScenicRow(Row row) {
        String name = getCellValue(row.getCell(0)).trim();
        if (!StringUtils.hasText(name)) {
            throw new BaseException("景点名称不能为空");
        }
        if (name.length() > 100) {
            throw new BaseException("景点名称过长（最多100字）");
        }

        ScenicSpot spot = new ScenicSpot();
        spot.setName(name);
        spot.setCity(emptyToNull(getCellValue(row.getCell(1))));
        spot.setCategory(emptyToNull(getCellValue(row.getCell(2))));
        spot.setRating(parseDecimal(getCellValue(row.getCell(3)), "评分", 0, 5.0));
        spot.setPrice(parseDecimal(getCellValue(row.getCell(4)), "价格", 0, null));
        spot.setOpenTime(emptyToNull(getCellValue(row.getCell(5))));
        spot.setAddress(emptyToNull(getCellValue(row.getCell(6))));
        spot.setDescription(emptyToNull(getCellValue(row.getCell(7))));
        spot.setCover(emptyToNull(getCellValue(row.getCell(8))));

        // 图片：多个用 | 或换行分隔
        String images = getCellValue(row.getCell(9));
        if (StringUtils.hasText(images)) {
            List<String> imageList = Arrays.stream(images.split("[|\\n]"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            spot.setImages(imageList.isEmpty() ? null : JSON.toJSONString(imageList));
        }

        spot.setIsHot(parseYesNo(getCellValue(row.getCell(10)), "热门", 0));
        spot.setSortOrder(parseIntSafe(getCellValue(row.getCell(11)), "排序", 0));
        spot.setStatus(parseYesNo(getCellValue(row.getCell(12)), "状态", StatusConstant.ENABLE));
        // 状态列语义：启用/禁用 → 1/0；热门列语义：是/否 → 1/0
        spot.setViewCount(0);
        spot.setCreateTime(LocalDateTime.now());
        spot.setUpdateTime(LocalDateTime.now());
        return spot;
    }

    /** 读取单元格值：字符串/数字/布尔统一转字符串，空返回 "" */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
            case NUMERIC:
                double v = cell.getNumericCellValue();
                return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "是" : "否";
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    /** 整行是否为空 */
    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (StringUtils.hasText(getCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    private String emptyToNull(String v) {
        return StringUtils.hasText(v) ? v : null;
    }

    /** 解析小数（评分/价格），支持范围校验 */
    private BigDecimal parseDecimal(String v, String field, double min, Double max) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v);
            if (d.doubleValue() < min || (max != null && d.doubleValue() > max)) {
                throw new BaseException(field + "超出范围（" + min + "~" + (max == null ? "不限" : max) + "）");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new BaseException(field + "必须是数字，当前值：" + v);
        }
    }

    private Integer parseIntSafe(String v, String field, int defaultVal) {
        if (!StringUtils.hasText(v)) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new BaseException(field + "必须是整数，当前值：" + v);
        }
    }

    /** 解析 是/否 或 1/0，默认值兜底 */
    private Integer parseYesNo(String v, String field, int defaultVal) {
        if (!StringUtils.hasText(v)) {
            return defaultVal;
        }
        if ("是".equals(v) || "1".equals(v) || "启用".equals(v)) {
            return 1;
        }
        if ("否".equals(v) || "0".equals(v) || "禁用".equals(v)) {
            return 0;
        }
        throw new BaseException(field + "只支持：是/否 或 1/0，当前值：" + v);
    }

    // ─────────── 活动 ───────────

    @Override
    public PageResult<ActivityInfo> activityPage(AdminContentPageDTO dto) {
        LambdaQueryWrapper<ActivityInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, ActivityInfo::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getCity()), ActivityInfo::getCity, dto.getCity())
                .eq(StringUtils.hasText(dto.getCategory()), ActivityInfo::getCategory, dto.getCategory())
                .like(StringUtils.hasText(dto.getKeyword()), ActivityInfo::getTitle, dto.getKeyword())
                .orderByAsc(ActivityInfo::getSortOrder);

        Page<ActivityInfo> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<ActivityInfo> result = activityInfoMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public void activitySave(ActivitySaveDTO dto) {
        if (dto.getId() == null) {
            ActivityInfo activity = new ActivityInfo();
            BeanUtils.copyProperties(dto, activity);
            if (activity.getStatus() == null) activity.setStatus(StatusConstant.ENABLE);
            if (activity.getSortOrder() == null) activity.setSortOrder(0);
            if (activity.getIsHot() == null) activity.setIsHot(0);
            if (activity.getEnrollCount() == null) activity.setEnrollCount(0);
            activity.setCreateTime(LocalDateTime.now());
            activity.setUpdateTime(LocalDateTime.now());
            activityInfoMapper.insert(activity);
            log.info("管理员新增活动：{}", activity.getTitle());
        } else {
            ActivityInfo activity = activityInfoMapper.selectById(dto.getId());
            if (activity == null) {
                throw new BaseException("活动不存在");
            }
            BeanUtils.copyProperties(dto, activity);
            activity.setUpdateTime(LocalDateTime.now());
            activityInfoMapper.updateById(activity);
            log.info("管理员编辑活动：{}", activity.getTitle());
        }
    }

    @Override
    public void activityDelete(Long id) {
        ActivityInfo activity = activityInfoMapper.selectById(id);
        if (activity == null) {
            throw new BaseException("活动不存在");
        }
        activityInfoMapper.deleteById(id);
        log.info("管理员删除活动：{}", activity.getTitle());
    }

    @Override
    public void activityStatus(Long id, Integer status) {
        ActivityInfo activity = activityInfoMapper.selectById(id);
        if (activity == null) {
            throw new BaseException("活动不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        ActivityInfo update = new ActivityInfo();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        activityInfoMapper.updateById(update);
    }
}
