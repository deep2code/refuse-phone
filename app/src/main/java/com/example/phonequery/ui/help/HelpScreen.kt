package com.example.phonequery.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.phonequery.ui.theme.AppCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phonequery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.help_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpandableHelpCard(title = "一、固定电话（座机）编码规则", initiallyExpanded = true) {
                Paragraph("国内固定电话由「区号 + 本地用户号码」两部分组成。")
                SubTitle("1. 区号（以 0 开头）")
                Bullets(
                    listOf(
                        "2 位区号：直辖市 / 特大城市 —— 北京 010、上海 021、天津 022、重庆 023",
                        "3 位区号：大部分地级市 —— 广州 020、深圳 0755、杭州 0571、成都 028、西安 029",
                        "4 位区号：少数地区 —— 如广东部分地市 076x 后补一位（如 0769 东莞）"
                    )
                )
                SubTitle("2. 本地号码")
                Bullets(
                    listOf(
                        "通常为 7～8 位；2 位区号城市多为 8 位，3 位区号城市多为 7 或 8 位"
                    )
                )
                SubTitle("3. 拨打方式")
                Bullets(
                    listOf(
                        "本地互拨：直接拨 7～8 位本地号码",
                        "国内长途：0 + 区号 + 本地号码（例：北京号码拨为 01012345678）",
                        "国际长途：+86 + 去掉首位 0 的区号 + 本地号码"
                    )
                )
                SubTitle("4. 特殊号码（非普通固话）")
                Bullets(
                    listOf(
                        "400 / 800：企业主被叫分摊 / 被叫付费号码，仅作呼入，不能外呼显示",
                        "95 / 96 开头：全国统一服务短号码（银行、运营商、大型企业客服）",
                        "这些号码常被冒充，来电时务必结合标记与场景判断"
                    )
                )
            }

            ExpandableHelpCard(title = "固定电话 3 位区号对照表（区号 → 城市）") {
                Paragraph("以下为国内 3 位长途区号与城市的完整对照。区号均以 0 开头；国际长途拨扣除首位 0 并加 +86（如北京写作 +86 10）。")
                AREA_CODE_TABLE.forEach { (province, items) ->
                    SubTitle(province)
                    Bullets(items)
                }
            }

            ExpandableHelpCard(title = "二、手机号码规律") {
                Paragraph("中国大陆手机号均为 11 位，以 1 开头，第二位为 3～9。其结构可拆为三段：")
                Bullets(
                    listOf(
                        "前 3 位（网络识别号 / 号段）：标识运营商和卡类型（移动 / 联通 / 电信 / 广电 / 虚商）",
                        "第 4～7 位（地区编码 H0H1H2H3）：决定号码归属地",
                        "后 4 位（用户号码）：运营商内部分配的流水号"
                    )
                )
                SubTitle("第 4～7 位：地区编码（HLR 码）")
                Paragraph("这 4 位是号码的「地区编码」，由运营商按省、市分配，标识号码的开户归属地（即 HLR 归属位置寄存器所在的城市）。归属地查询正是以「前 3 位 + 这 4 位 = 前 7 位」整体比对号段库得到的。")
                Paragraph("早期手机号常把长途区号直接编入这 4 位（如北京 010、上海 021、哈尔滨 0451、深圳 0755），所以中间段往往能看出开户地；现在号段规划更复杂，同一组 4 位在不同运营商（前 3 位）下可能对应不同城市。以下为常见地区码示例（仅供理解规律，实际以应用查询为准）：")
                Bullets(
                    listOf(
                        "北京：地区码常含 010 段",
                        "上海：021 段",
                        "广州：020 段",
                        "深圳：0755 段",
                        "成都：028 段",
                        "杭州：0571 段",
                        "南京：025 段",
                        "武汉：027 段",
                        "西安：029 段",
                        "哈尔滨：0451 段",
                        "重庆：023 段",
                        "天津：022 段",
                        "苏州：0512 段",
                        "沈阳：024 段"
                    )
                )
                SubTitle("常见运营商号段")
                Bullets(
                    listOf(
                        "中国移动：134-139、147、148、150-152、157-159、172、178、182-184、187-188、195、197、198 等",
                        "中国联通：130-132、145、146、155-156、166、167、171、175-176、185-186、196 等",
                        "中国电信：133、149、153、173、174、177、180-181、189、190、191、193、199 等",
                        "中国广电：192",
                        "虚拟运营商（转售，骚扰高发）：162、165、167、170、171、174 等"
                    )
                )
                SubTitle("防骗提示")
                Bullets(
                    listOf(
                        "境外来电常带 +00、+852（香港）、+853（澳门）、+886 等前缀，需提高警惕",
                        "改号软件可伪造显示为本地的 11 位号码，勿仅凭「屏幕上显示的号码」判断对方身份",
                        "对标记为「骚扰 / 营销 / 诈骗」或来自陌生虚商号段的来电，建议谨慎接听"
                    )
                )
            }
        }
    }
}

@Composable
private fun ExpandableHelpCard(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    AppCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * 固定电话 3 位长途区号 → 城市对照表，按省份分组。
 * 区号均以 0 开头；部分城市共用同一区号（如 阜阳·亳州 0558、深圳 0755 单列）。
 */
private val AREA_CODE_TABLE = listOf(
    "直辖市 / 特别行政区" to listOf(
        "010 北京市", "021 上海市", "022 天津市", "023 重庆市",
        "852 香港", "853 澳门"
    ),
    "河北省" to listOf(
        "0310 邯郸", "0311 石家庄", "0312 保定", "0313 张家口", "0314 承德",
        "0315 唐山", "0316 廊坊", "0317 沧州", "0318 衡水", "0319 邢台", "0335 秦皇岛"
    ),
    "山西省" to listOf(
        "0349 朔州", "0350 忻州", "0351 太原", "0352 大同", "0353 阳泉",
        "0354 晋中", "0355 长治", "0356 晋城", "0357 临汾", "0358 吕梁", "0359 运城"
    ),
    "内蒙古自治区" to listOf(
        "0470 呼伦贝尔", "0471 呼和浩特", "0472 包头", "0473 乌海", "0474 乌兰察布",
        "0475 通辽", "0476 赤峰", "0477 鄂尔多斯", "0478 巴彦淖尔", "0479 锡林郭勒盟", "0482 兴安盟", "0483 阿拉善盟"
    ),
    "辽宁省" to listOf(
        "024 沈阳", "0410 铁岭", "0411 大连", "0412 鞍山", "0413 抚顺",
        "0414 本溪", "0415 丹东", "0416 锦州", "0417 营口", "0418 阜新",
        "0419 辽阳", "0421 朝阳", "0427 盘锦", "0429 葫芦岛"
    ),
    "吉林省" to listOf(
        "0431 长春", "0432 吉林", "0433 延边", "0434 四平", "0435 通化",
        "0436 白城", "0437 辽源", "0438 松原", "0439 白山"
    ),
    "黑龙江省" to listOf(
        "0451 哈尔滨", "0452 齐齐哈尔", "0453 牡丹江", "0454 佳木斯", "0455 绥化",
        "0456 黑河", "0457 大兴安岭", "0458 伊春", "0459 大庆", "0464 七台河", "0467 鸡西", "0468 鹤岗", "0469 双鸭山"
    ),
    "江苏省" to listOf(
        "025 南京", "0510 无锡", "0511 镇江", "0512 苏州", "0513 南通",
        "0514 扬州", "0515 盐城", "0516 徐州", "0517 淮安", "0518 连云港", "0519 常州",
        "0523 泰州", "0527 宿迁"
    ),
    "浙江省" to listOf(
        "0570 衢州", "0571 杭州", "0572 湖州", "0573 嘉兴", "0574 宁波",
        "0575 绍兴", "0576 台州", "0577 温州", "0578 丽水", "0579 金华", "0580 舟山"
    ),
    "安徽省" to listOf(
        "0550 滁州", "0551 合肥", "0552 蚌埠", "0553 芜湖", "0554 淮南",
        "0555 马鞍山", "0556 安庆", "0557 宿州", "0558 阜阳·亳州", "0559 黄山",
        "0561 淮北", "0562 铜陵", "0563 宣城", "0564 六安", "0566 池州"
    ),
    "福建省" to listOf(
        "0591 福州", "0592 厦门", "0593 宁德", "0594 莆田", "0595 泉州",
        "0596 漳州", "0597 龙岩", "0598 三明", "0599 南平"
    ),
    "江西省" to listOf(
        "0701 鹰潭", "0790 新余", "0791 南昌", "0792 九江", "0793 上饶",
        "0794 抚州", "0795 宜春", "0796 吉安", "0797 赣州", "0798 景德镇", "0799 萍乡"
    ),
    "山东省" to listOf(
        "0530 菏泽", "0531 济南", "0532 青岛", "0533 淄博", "0534 德州",
        "0535 烟台", "0536 潍坊", "0537 济宁", "0538 泰安", "0539 临沂",
        "0543 滨州", "0546 东营", "0631 威海", "0632 枣庄", "0633 日照", "0635 聊城"
    ),
    "河南省" to listOf(
        "0370 商丘", "0371 郑州", "0372 安阳", "0373 新乡", "0374 许昌",
        "0375 平顶山", "0376 信阳", "0377 南阳", "0378 开封", "0379 洛阳",
        "0391 焦作·济源", "0392 鹤壁", "0393 濮阳", "0394 周口", "0395 漯河", "0396 驻马店", "0398 三门峡"
    ),
    "湖北省" to listOf(
        "027 武汉", "0710 襄阳", "0711 鄂州", "0712 孝感", "0713 黄冈",
        "0714 黄石", "0715 咸宁", "0716 荆州", "0717 宜昌", "0718 恩施", "0719 十堰",
        "0722 随州", "0724 荆门"
    ),
    "湖南省" to listOf(
        "0730 岳阳", "0731 长沙", "0732 湘潭", "0733 株洲", "0734 衡阳",
        "0735 郴州", "0736 常德", "0737 益阳", "0738 娄底", "0739 邵阳",
        "0743 湘西", "0744 张家界", "0745 怀化", "0746 永州"
    ),
    "广东省" to listOf(
        "020 广州", "0660 汕尾", "0662 阳江", "0663 揭阳", "0668 茂名",
        "0750 江门", "0751 韶关", "0752 惠州", "0753 梅州", "0754 汕头", "0755 深圳",
        "0756 珠海", "0757 佛山", "0758 肇庆", "0759 湛江", "0762 河源", "0763 清远",
        "0766 云浮", "0768 潮州", "0769 东莞"
    ),
    "广西壮族自治区" to listOf(
        "0770 防城港", "0771 南宁·崇左", "0772 柳州·来宾", "0773 桂林", "0774 梧州·贺州",
        "0775 玉林·贵港", "0776 百色", "0777 钦州", "0778 河池", "0779 北海"
    ),
    "海南省" to listOf(
        "0898 海口·三沙", "0899 三亚", "0890 儋州"
    ),
    "四川省" to listOf(
        "028 成都", "0812 攀枝花", "0813 自贡", "0816 绵阳", "0817 南充",
        "0818 达州", "0825 遂宁", "0826 广安", "0827 巴中", "0830 泸州",
        "0831 宜宾", "0832 资阳·内江", "0833 乐山·眉山", "0834 凉山", "0835 雅安",
        "0836 甘孜", "0837 阿坝", "0838 德阳", "0839 广元"
    ),
    "贵州省" to listOf(
        "0851 贵阳", "0852 遵义", "0853 安顺", "0854 黔南", "0855 黔东南",
        "0856 铜仁", "0857 毕节", "0858 六盘水", "0859 黔西南"
    ),
    "云南省" to listOf(
        "0691 西双版纳", "0692 德宏", "0870 昭通", "0871 昆明", "0872 大理",
        "0873 红河", "0874 曲靖", "0875 保山", "0876 文山", "0877 玉溪", "0878 楚雄",
        "0879 普洱", "0883 临沧", "0886 怒江", "0887 迪庆", "0888 丽江"
    ),
    "西藏自治区" to listOf(
        "0891 拉萨", "0892 日喀则", "0893 山南", "0894 林芝", "0895 昌都", "0896 那曲", "0897 阿里"
    ),
    "陕西省" to listOf(
        "029 西安", "0910 咸阳", "0911 延安", "0912 榆林", "0913 渭南",
        "0914 商洛", "0915 安康", "0916 汉中", "0917 宝鸡", "0919 铜川"
    ),
    "甘肃省" to listOf(
        "0930 临夏", "0931 兰州", "0932 定西", "0933 平凉", "0934 庆阳",
        "0935 武威·陇南", "0936 张掖", "0937 酒泉·嘉峪关", "0938 天水", "0941 甘南", "0943 白银"
    ),
    "青海省" to listOf(
        "0970 海北", "0971 西宁", "0972 海东", "0973 黄南", "0974 海南",
        "0975 果洛", "0976 玉树", "0977 海西"
    ),
    "宁夏回族自治区" to listOf(
        "0951 银川", "0952 石嘴山", "0953 吴忠·中卫", "0954 固原"
    ),
    "新疆维吾尔自治区" to listOf(
        "0901 塔城", "0902 哈密", "0903 和田", "0906 阿勒泰", "0908 克孜勒苏", "0909 博尔塔拉",
        "0990 克拉玛依", "0991 乌鲁木齐", "0994 昌吉", "0995 吐鲁番", "0996 巴音郭楞",
        "0997 阿克苏", "0998 喀什", "0999 伊犁"
    )
)
