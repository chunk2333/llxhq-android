package cn.llxhq.consumer;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * 下载链接数据：内置节点（按运营商 / 厂商分组）+ 用户自定义节点（持久化）
 * 内置节点移植自 uu6/llxhq 网页版的节点列表。
 */
public final class LinkStore {

    public static final class Node {
        public final String name;
        public final String url;
        public final String group;
        public final boolean custom;

        public Node(String group, String name, String url, boolean custom) {
            this.group = group;
            this.name = name;
            this.url = url;
            this.custom = custom;
        }
    }

    public static final String GROUP_GENERAL = "通用大文件";
    public static final String GROUP_CUSTOM = "我的自定义";

    /** 内置节点 */
    public static final Node[] PRESETS = new Node[]{
            // ---------------- 通用大文件 ----------------
            new Node(GROUP_GENERAL, "清华镜像 · Ubuntu ISO（3.8G）",
                    "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-releases/noble/ubuntu-24.04.5-live-server-amd64.iso", false),
            new Node(GROUP_GENERAL, "阿里云镜像 · Ubuntu ISO（3.8G）",
                    "https://mirrors.aliyun.com/ubuntu-releases/noble/ubuntu-24.04.5-live-server-amd64.iso", false),
            new Node(GROUP_GENERAL, "华为云镜像 · Ubuntu ISO（3.8G）",
                    "https://mirrors.huaweicloud.com/ubuntu-releases/noble/ubuntu-24.04.5-live-server-amd64.iso", false),
            new Node(GROUP_GENERAL, "Cloudflare 动态文件 · 100MB",
                    "https://speed.cloudflare.com/__down?bytes=100000000", false),
            new Node(GROUP_GENERAL, "Cachefly 50MB（原版默认节点）",
                    "https://cachefly.cachefly.net/50mb.test", false),

            // ---------------- 中国移动 ----------------
            new Node("中国移动", "咪咕音乐",
                    "https://d.musicapp.migu.cn/upload/fbpt_rsync_apps/local/signed/MobileMusic7411/MobileMusic7411_014000D.apk", false),
            new Node("中国移动", "和彩云 1",
                    "https://img.mcloud.139.com/material_prod/material_media/20221128/1669626861087.png", false),
            new Node("中国移动", "咪咕快游 1",
                    "https://plaza.cmgame.com:8443/resource/upload/records/tvapk/apk/6.9.1.0_0_5132_2021-12-29_10011000000_xw_sec_signed_signed_signed.apk", false),
            new Node("中国移动", "咪咕快游 2",
                    "https://pc-dl.migufun.com:8443/channelpackage/mgame-2djSBy.exe", false),
            new Node("中国移动", "咪咕快游 3",
                    "https://freeserver.migufun.com/resource/beta/video/system/20210924112351666.mp4", false),
            new Node("中国移动", "咪咕快游 4",
                    "https://freeserver.migufun.com/resource/beta/apk/20231114094513/MiguPlay-V3.69.1.1_miguzsj.apk", false),

            // ---------------- 中国电信 ----------------
            new Node("中国电信", "天翼云 1",
                    "https://desk.ctyun.cn/desktop/software/clientsoftware/download/5961fa22ee369eba433da6b0247eb11f", false),
            new Node("中国电信", "天翼云 2",
                    "https://desk.ctyun.cn:8999/desktop-prod/software/android_client/20/64/102010101/clouddesktoc_phone_2.1.1_452_prod_102010101_2.1.1_signed.apk", false),
            new Node("中国电信", "天翼云 3 · 翼加密客户端",
                    "https://desk.ctyun.cn/desktop/software/clientsoftware/download/50218cd4d2286623049e374061cffd0e", false),
            new Node("中国电信", "天翼云应用市场",
                    "https://desk.ctyun.cn/desktop/software/clientsoftware/download/ff3e71dcc21152307f54700c62e5aef6", false),

            // ---------------- 中国联通 ----------------
            new Node("中国联通", "联通电视",
                    "https://listen.10155.com/listener/womusic-bucket/90115000/mv_vod/volte_mp4/20230215/1625752132487675906.mp4", false),
            new Node("中国联通", "联通官网静态资源 1",
                    "https://m1.ad.10010.com/small_video/uploadImg/1669798519261.png", false),

            // ---------------- 百度 ----------------
            new Node("百度", "爱奇艺客户端",
                    "https://bdcdncnc.inter.71edge.com/cdn/pca/20231130/10.9.1.7348/channel/1701328986348/IQIYIsetup_z43.exe", false),
            new Node("百度", "百度对象存储资源",
                    "https://fanyi-cdn.cdn.bcebos.com/static/api-ssr/static/js/2.1c6a3a74.chunk.js", false),

            // ---------------- 阿里系 ----------------
            new Node("阿里系", "阿里图片 CDN 域名加速",
                    "https://img.alicdn.com/imgextra/i1/O1CN01xA4P9S1JsW2WEg0e1_!!6000000001084-2-tps-2880-560.png", false),
            new Node("阿里系", "支付宝开放平台 1",
                    "https://mdn.alipayobjects.com/ind_developertool/afts/file/A*fSAmSbgxLosAAAAAAAAAAAAADlx-AQ?af_fileName=AlipayKeyTool-2.0.3.dmg", false),
            new Node("阿里系", "支付宝开放平台 2",
                    "https://gw.alipayobjects.com/render/p/yuyan/180020010001210191/umi.9f3e4149.js", false),
            new Node("阿里系", "阿里系 Gw",
                    "https://gw.alicdn.com/tfscom/TB1fASCxhjaK1RjSZKzXXXVwXXa.jpg", false),

            // ---------------- 腾讯 ----------------
            new Node("腾讯", "腾讯游戏 · 静态资源加速 1",
                    "https://game.gtimg.cn/images/nz/web202106/index/bc_part1.gif?0.3190485611376561", false),
            new Node("腾讯", "Wegame · 静态资源加速",
                    "https://wegame.gtimg.com/g.55555-r.c4663/wegame-home/sc02-03.514d7db8.png", false),

            // ---------------- 京东 ----------------
            new Node("京东", "京东商城图片 CDN 服务",
                    "https://img10.360buyimg.com/live/jfs/t1/128947/12/26918/1361527/6260e71bE0ee85af5/ecaa17ea8dd3dddb.jpg", false),

            // ---------------- 网易 ----------------
            new Node("网易", "逆水寒官网静态资源",
                    "https://n.v.netease.com/2022/1206/de4b6add85f1537da839bdb5a501253d.mp4", false),
            new Node("网易", "逆水寒客户端",
                    "https://nsh.gdl.netease.com/NGP/NGP_NSH_2.0.81143.exe", false),
            new Node("网易", "MCLauncher 网易版（阿里 CDN）",
                    "https://x19.gdl.netease.com/MCLauncher_1.10.0.15222.exe", false),
            new Node("网易", "网易公开课（白云山 CDN）",
                    "https://mov.bn.netease.com/open-movie/nos/mp4/2015/11/26/SB8ECV1ST_sd.mp4", false),
            new Node("网易", "网易云游戏",
                    "https://nsh.gdl.netease.com/cloudgame_macos/NSH_cloud_game_for_mac_1.4.6_2023_06-02-11.dmg", false),

            // ---------------- 蓝绿大厂 ----------------
            new Node("蓝绿大厂", "vivo 官网静态资源",
                    "https://wwwstatic.vivo.com.cn/vivoportal/files/resource/funtouch/1651200648928/images/os2-jude-video.mp4", false),
            new Node("蓝绿大厂", "OPPO 商城静态资源",
                    "https://dsfs.oppo.com/oppo/shop-pc-v2/main/js/9fb472f.js", false),
            new Node("蓝绿大厂", "vivo 应用商店",
                    "https://imgwsdl.vivo.com.cn/appstore/developer/soft/20210125/202101251051528awqa.apk", false),

            // ---------------- 字节跳动 ----------------
            new Node("字节跳动", "字节 CDN 资源",
                    "https://lf9-cdn-tos.bytecdntp.com/cdn/yuntu-index/1.0.4/case/maiteng/detailbg.png", false),
            new Node("字节跳动", "字节 CDN 资源 2",
                    "https://lf9-apk.ugapk.cn/package/apk/bgame/1593_112/bgame_operation_update_2508_v1593_112_63aa_1693882489.apk?v=1693882509", false),
            new Node("字节跳动", "抖音 Win 客户端",
                    "https://www.douyin.com/download/pc/obj/douyin-pc-client/7044145585217083655/releases/11259813/3.0.1/win32-ia32/douyin-downloader-v3.0.1-win32-ia32-douyinDownload1.exe", false),
            new Node("字节跳动", "今日头条安卓客户端",
                    "https://lf9-apk.ugapk.cn/package/apk/news_article/1001_9660/news_article_tt_wtt_qrcod_v1001_9660_4cd4_1706007871.apk?v=1706007877", false),
            new Node("字节跳动", "飞书 Mac 客户端",
                    "https://sf3-cn.feishucdn.com/obj/ee-appcenter/9323162e/Feishu-darwin_x64-7.4.9-signed.dmg", false),

            // ---------------- 哔哩哔哩 ----------------
            new Node("哔哩哔哩", "哔哩哔哩静态资源",
                    "https://activity.hdslb.com/blackboard/static/20210604/4d40bc4f98f94fbc71c235832ce3efd4/hJEhL6jGOY.zip", false),

            // ---------------- 华为 ----------------
            new Node("华为", "华为 P60 Pro 素材",
                    "https://consumer-img.huawei.com/content/dam/huawei-cbg-site/common/mkt/pdp/phones/p60-pro/images/camera/huawei-p60-pro-camera-ui.mp4", false),

            // ---------------- 锤子 ----------------
            new Node("锤子", "Smartisan OS 官网公共资源",
                    "https://static.smartisanos.cn/common/video/production/ocean/os-1-1710.mp4", false),

            // ---------------- 拼多多 ----------------
            new Node("拼多多", "拼多多 H5 官网静态资源（图像）",
                    "https://funimg.pddpic.com/c3affbeb-9b31-4546-b2df-95b62de81639.png.slim.png", false),
            new Node("拼多多", "拼多多 IMG/CDN 资源文件",
                    "https://t00img.yangkeduo.com/chat/images/2022-12-12/79e0a4684de85c7b59f797819260be98.jpeg", false),

            // ---------------- 国内其他大厂 ----------------
            new Node("国内其他大厂", "CCTV",
                    "https://dh5.cntv.myhwcdn.cn/asp/h5e/hls/1200/0303000a/3/default/ec48b9f8c76e49af842d4942914ad663/1.ts", false),
            new Node("国内其他大厂", "AcFun",
                    "https://cdn.aixifan.com/downloads/AcFun-acfunh5-release-6.62.0.1238_x64_60b608.apk", false),
            new Node("国内其他大厂", "斗鱼 CDN（小文件）",
                    "https://shark2.douyucdn.cn/front-publish/live-master/lib/vendor-room_4e3a873.js", false),
            new Node("国内其他大厂", "七牛云",
                    "https://dn-mars-assets.qbox.me/qiniulog/img-slogan-white-en.png", false),
            new Node("国内其他大厂", "鲜牛加速器（华为云 CDN）",
                    "https://picture.xianniu.com/pc/download/4.6.9.3/xianniusetup.4.6.9.3.exe", false),
            new Node("国内其他大厂", "GitHub 文件加速（Cloudflare CDN）",
                    "https://gh.con.sh/https://github.com/AaronFeng753/Waifu2x-Extension-GUI/releases/download/v2.21.12/Waifu2x-Extension-GUI-v2.21.12-Portable.7z", false),

            // ---------------- 海外优选 ----------------
            new Node("海外优选", "Cachefly 100MB",
                    "https://cachefly.cachefly.net/100mb.test", false),
            new Node("海外优选", "Cloudflare 25MB",
                    "https://speed.cloudflare.com/__down?bytes=25000000", false),
            new Node("海外优选", "Vultr 1G（新加坡）",
                    "https://sgp-ping.vultr.com/vultr.com.1000MB.bin", false),
            new Node("海外优选", "Vultr 1G（纽约）",
                    "https://nj-us-ping.vultr.com/vultr.com.1000MB.bin", false),
            new Node("海外优选", "Linode 100MB（东京）",
                    "https://speedtest.tokyo2.linode.com/100MB-tokyo2.bin", false),
            new Node("海外优选", "BBC 中文网",
                    "https://emp.bbci.co.uk/emp/dashjs/3.2.0-8/dash.all.min.js", false)
    };

    private static final String SEP_ITEM = "\n";
    private static final String SEP_FIELD = "\t";

    private LinkStore() {
    }

    public static List<Node> all(Context c) {
        List<Node> list = new ArrayList<>();
        for (Node n : PRESETS) {
            list.add(n);
        }
        list.addAll(customNodes(c));
        return list;
    }

    public static List<Node> customNodes(Context c) {
        List<Node> list = new ArrayList<>();
        String raw = new Prefs(c).getCustomLinks();
        if (raw == null || raw.length() == 0) {
            return list;
        }
        String[] items = raw.split(SEP_ITEM);
        for (String item : items) {
            if (item == null || item.length() == 0) {
                continue;
            }
            String[] f = item.split(SEP_FIELD, 2);
            if (f.length < 2) {
                continue;
            }
            String name = f[0];
            String url = f[1];
            if (url.length() == 0) {
                continue;
            }
            list.add(new Node(GROUP_CUSTOM, name.length() == 0 ? url : name, url, true));
        }
        return list;
    }

    /** 新增自定义节点；同链接已存在时返回 false */
    public static boolean addCustom(Context c, String name, String url) {
        name = clean(name);
        url = clean(url);
        if (url.length() == 0) {
            return false;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        List<Node> customs = customNodes(c);
        for (Node n : customs) {
            if (n.url.equals(url)) {
                return false;
            }
        }
        customs.add(new Node(GROUP_CUSTOM, name.length() == 0 ? url : name, url, true));
        save(c, customs);
        return true;
    }

    public static void removeCustom(Context c, String url) {
        List<Node> customs = customNodes(c);
        List<Node> keep = new ArrayList<>();
        for (Node n : customs) {
            if (!n.url.equals(url)) {
                keep.add(n);
            }
        }
        save(c, keep);
        Prefs p = new Prefs(c);
        if (p.getSelectedUrl().equals(url)) {
            p.setSelectedUrl(PRESETS[0].url);
        }
    }

    /** 根据当前选中的 URL 找到节点（找不到则作为自定义节点展示） */
    public static Node findSelected(Context c) {
        String url = new Prefs(c).getSelectedUrl();
        for (Node n : all(c)) {
            if (n.url.equals(url)) {
                return n;
            }
        }
        return new Node(GROUP_CUSTOM, "自定义链接", url, true);
    }

    private static void save(Context c, List<Node> customs) {
        StringBuilder sb = new StringBuilder();
        for (Node n : customs) {
            if (sb.length() > 0) {
                sb.append(SEP_ITEM);
            }
            sb.append(clean(n.name)).append(SEP_FIELD).append(clean(n.url));
        }
        new Prefs(c).setCustomLinks(sb.toString());
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\n", " ").replace("\t", " ").trim();
    }
}
