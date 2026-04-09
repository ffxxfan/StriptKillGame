package com.example.striptkillgamedemo2.entity.enums;

/**
 * 线索类型枚举。
 * <p>
 * 标识一条游戏线索的媒体形式，用于前端按不同方式呈现（纯文本 / 图片 / 视频）。
 * </p>
 */
public enum ClueType {
    /** 文本线索：以文字描述形式呈现。 */
    TEXT,
    /** 图片线索：以图片资源呈现，需配合资源 URL。 */
    IMAGE,
    /** 视频线索：以视频资源呈现，需配合资源 URL。 */
    VIDEO
}
