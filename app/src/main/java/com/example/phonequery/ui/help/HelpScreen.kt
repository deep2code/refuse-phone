package com.example.phonequery.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
                SubTitle("5. 在本应用中的用法")
                Paragraph("在「黑白名单」里输入区号（如 010、0755）并选择「号段/区号」类型，即可整段屏蔽该地区的全部固话来电，应对异地营销骚扰。")
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
                Paragraph("归属地判断正是基于「前 7 位」，本应用的离线号段库即按此规则匹配。")
                Paragraph("在本应用查询页输入任意 11 位手机号，即可离线显示归属地（省 / 市 / 运营商）。默认使用 Google libphonenumber 的离线数据（省 / 市 + 运营商，开箱即用）；若想要号段级更精确、更新更全的结果，可运行 scripts/fetch_phonedata.py 生成 phonedata.db 放入 assets 后重新打包（无需联网即可生效）。")
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
                        "对标记为「骚扰 / 营销 / 诈骗」或来自陌生虚商号段的来电，建议结合本应用标记谨慎接听"
                    )
                )
            }

            ExpandableHelpCard(title = "三、本应用如何识别与拦截") {
                Bullets(
                    listOf(
                        "离线号段库：根据前 7 位判断归属地，断网也能用",
                        "号段 / 区号规则：一条前缀规则即可拦截整段号码，应对营销公司频繁换号",
                        "黑名单无限容量：不受 50 条上限限制，整段号段或某一区号一条规则全拦截",
                        "一键屏蔽虚商号段：可整段拦截 170/171/162/165/167/174 等高风险号段",
                        "在线标记本地缓存：查询过的号码标记会本地保存 90 天，断网或接口失效时仍可用于识别来电，越用越准"
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
