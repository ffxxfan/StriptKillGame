package com.example.striptkillgamedemo2.config;

import com.example.striptkillgamedemo2.entity.enums.ClueType;
import com.example.striptkillgamedemo2.entity.enums.ScriptDifficulty;
import com.example.striptkillgamedemo2.entity.mongo.Clue;
import com.example.striptkillgamedemo2.entity.mongo.Role;
import com.example.striptkillgamedemo2.entity.mongo.Script;
import com.example.striptkillgamedemo2.entity.mongo.ScriptStage;
import com.example.striptkillgamedemo2.repository.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 开发环境测试数据初始化器。
 * 仅在 scripts 集合为空时执行插入，避免重复。
 * 激活方式：application.properties 中添加 spring.profiles.active=dev
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ScriptRepository scriptRepository;

    @Override
    public void run(String... args) {
        if (scriptRepository.count() > 0) {
            log.info("Scripts already exist, skipping data initialization.");
            return;
        }
        log.info("Inserting 10 test scripts...");
        scriptRepository.saveAll(buildScripts());
        log.info("Test data inserted successfully.");
    }

    private List<Script> buildScripts() {
        return List.of(
                buildScript1(),
                buildScript2(),
                buildScript3(),
                buildScript4(),
                buildScript5(),
                buildScript6(),
                buildScript7(),
                buildScript8(),
                buildScript9(),
                buildScript10()
        );
    }

    // ─── 1. 深海疑云 ───────────────────────────────────────────────────────────
    private Script buildScript1() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();

        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("船长林峰").avatar("").isNpc(false)
                        .secret("我在航行日志中涂改了时间").locationTag("船长室")
                        .selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("医生苏晴").avatar("").isNpc(false)
                        .secret("我私藏了一剂镇静药").locationTag("医疗室")
                        .selfClueIds(List.of(clue2.toHexString())).searchPower(2).build(),
                Role.builder().id(roleC).name("乘客赵明").avatar("").isNpc(true)
                        .prompt("你是神秘乘客，不透露真实身份").secret("我是卧底侦探")
                        .locationTag("客舱").selfClueIds(List.of(clue3.toHexString())).searchPower(2).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("涂改的航行日志").type(ClueType.TEXT)
                        .content("日志第7页有明显涂改痕迹，原始时间被修改为晚2小时。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleB.toHexString()))
                        .locationTag(List.of("船长室")).build(),
                Clue.builder().id(clue2).title("空药瓶").type(ClueType.TEXT)
                        .content("标签写着\"镇静剂\"，剂量足以使人昏迷4小时。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString()))
                        .locationTag(List.of("医疗室")).build(),
                Clue.builder().id(clue3).title("神秘徽章").type(ClueType.TEXT)
                        .content("徽章背面刻有\"海关稽查局\"字样。")
                        .isInitialHidden(false).searchableRoleIds(List.of(roleA.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("客舱")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("发现尸体")
                        .contentMap(Map.of(roleA.toHexString(), "你发现乘客室有人死亡，需要稳住局面。",
                                roleB.toHexString(), "被要求检验尸体，你注意到死亡时间与日志不符。"))
                        .unlockClueIds(List.of(clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("交叉审问")
                        .contentMap(Map.of(roleA.toHexString(), "对方开始怀疑你修改了日志。",
                                roleC.toHexString(), "你需要在不暴露身份的情况下收集证据。"))
                        .unlockClueIds(List.of(clue1.toHexString(), clue2.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("揭露真相")
                        .contentMap(Map.of(roleA.toHexString(), "所有证据指向你，如何自辩？",
                                roleB.toHexString(), "你掌握了关键证据。",
                                roleC.toHexString(), "是时候公开你的真实身份了。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("深海疑云").description("一艘远洋货轮上发生了离奇死亡事件，真相隐藏在深海之中。")
                .difficulty(ScriptDifficulty.NORMAL).playerCount(3).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 2. 古宅迷踪 ───────────────────────────────────────────────────────────
    private Script buildScript2() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId roleD = new ObjectId();

        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();
        ObjectId clue4 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("长女陈雪").avatar("").isNpc(false)
                        .secret("我知道父亲秘密账户的密码").locationTag("书房").selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("次子陈磊").avatar("").isNpc(false)
                        .secret("我伪造了父亲的遗嘱").locationTag("卧室").selfClueIds(List.of(clue2.toHexString())).searchPower(3).build(),
                Role.builder().id(roleC).name("管家王伯").avatar("").isNpc(true)
                        .prompt("你是忠诚的老管家，知道很多秘密但不主动透露").secret("我目睹了当晚发生的事")
                        .locationTag("厨房").selfClueIds(List.of(clue3.toHexString())).searchPower(2).build(),
                Role.builder().id(roleD).name("侦探李明").avatar("").isNpc(false)
                        .secret("我与家族有私人恩怨").locationTag("大厅").selfClueIds(List.of(clue4.toHexString())).searchPower(4).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("遗嘱原件").type(ClueType.TEXT)
                        .content("遗嘱显示财产平均分配，但有被人翻动过的痕迹。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("书房")).build(),
                Clue.builder().id(clue2).title("伪造的遗嘱").type(ClueType.TEXT)
                        .content("字迹模仿得很像，但笔压与老爷惯用手法不符。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("卧室")).build(),
                Clue.builder().id(clue3).title("管家日记").type(ClueType.TEXT)
                        .content("日记中记录了当晚11点听到争吵声，随后是玻璃破碎的声音。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleC.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("厨房")).build(),
                Clue.builder().id(clue4).title("碎玻璃").type(ClueType.TEXT)
                        .content("书房窗户内侧的碎玻璃，说明窗户是从内部打碎的。")
                        .isInitialHidden(false).searchableRoleIds(List.of(roleA.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("书房")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("老爷之死")
                        .contentMap(Map.of(roleA.toHexString(), "父亲昨夜猝死，遗嘱下落不明。",
                                roleB.toHexString(), "你需要在遗嘱被找到前转移注意力。"))
                        .unlockClueIds(List.of(clue4.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("遗嘱风波")
                        .contentMap(Map.of(roleA.toHexString(), "你找到了原版遗嘱，但内容与律师说的不同。",
                                roleD.toHexString(), "你注意到两份遗嘱的差异。"))
                        .unlockClueIds(List.of(clue1.toHexString(), clue2.toHexString(), clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("真凶现形")
                        .contentMap(Map.of(roleB.toHexString(), "证据已指向你，如何应对？",
                                roleD.toHexString(), "所有线索汇聚，真相即将大白。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("古宅迷踪").description("百年老宅内，家族长辈离奇死亡，遗嘱疑云笼罩，每个人都有嫌疑。")
                .difficulty(ScriptDifficulty.HARD).playerCount(4).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 3. 校园密码 ───────────────────────────────────────────────────────────
    private Script buildScript3() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("学生会长周杰").avatar("").isNpc(false)
                        .secret("我知道老师收受贿赂的证据").locationTag("学生会办公室").selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("转学生林悦").avatar("").isNpc(false)
                        .secret("我转学是为了调查哥哥的失踪").locationTag("图书馆").selfClueIds(List.of(clue2.toHexString())).searchPower(2).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("加密U盘").type(ClueType.TEXT)
                        .content("U盘内有一份教师收款记录，密码是学校建校年份。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString()))
                        .locationTag(List.of("学生会办公室")).build(),
                Clue.builder().id(clue2).title("失踪者日记").type(ClueType.TEXT)
                        .content("日记最后一页写着：'我知道了真相，但我不敢说出来。'")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString()))
                        .locationTag(List.of("图书馆")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("神秘失踪")
                        .contentMap(Map.of(roleA.toHexString(), "学校有学生失踪，风声越来越紧。",
                                roleB.toHexString(), "你终于找到了哥哥可能去过的最后一个地方。"))
                        .unlockClueIds(List.of(clue1.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("真相揭露")
                        .contentMap(Map.of(roleA.toHexString(), "U盘内容让你进退两难。",
                                roleB.toHexString(), "日记里的线索指向了意想不到的人。"))
                        .unlockClueIds(List.of(clue2.toHexString())).build()
        );

        return Script.builder().title("校园密码").description("平静的校园下暗流涌动，一个学生的失踪牵出惊天秘密。")
                .difficulty(ScriptDifficulty.EASY).playerCount(2).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 4. 午夜列车 ───────────────────────────────────────────────────────────
    private Script buildScript4() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("列车长吴刚").avatar("").isNpc(false)
                        .secret("我在隧道段关闭了监控").locationTag("驾驶室").selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("商人钱伟").avatar("").isNpc(false)
                        .secret("我携带了不明箱子").locationTag("卧铺车厢").selfClueIds(List.of(clue2.toHexString())).searchPower(2).build(),
                Role.builder().id(roleC).name("记者何欣").avatar("").isNpc(false)
                        .secret("我正在追踪一起走私案").locationTag("餐车").selfClueIds(List.of(clue3.toHexString())).searchPower(3).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("监控盲区记录").type(ClueType.TEXT)
                        .content("隧道段监控被手动关闭，时间恰好是案发前30分钟。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("驾驶室")).build(),
                Clue.builder().id(clue2).title("神秘箱子").type(ClueType.TEXT)
                        .content("箱子双重锁闭，X光显示内有文件和大量现金。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("卧铺车厢")).build(),
                Clue.builder().id(clue3).title("记者笔记").type(ClueType.TEXT)
                        .content("笔记中记录了走私团伙的联络暗语，其中一个正是本次列车的车次号。")
                        .isInitialHidden(false).searchableRoleIds(List.of(roleC.toHexString()))
                        .locationTag(List.of("餐车")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("午夜惊魂")
                        .contentMap(Map.of(roleA.toHexString(), "列车突然紧急停车，一名乘客报告有人在卧铺失踪。",
                                roleC.toHexString(), "你意识到这与你追踪的案子有关。"))
                        .unlockClueIds(List.of(clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("隧道秘密")
                        .contentMap(Map.of(roleA.toHexString(), "有人发现了监控盲区的秘密。",
                                roleB.toHexString(), "你的箱子引起了所有人的注意。"))
                        .unlockClueIds(List.of(clue1.toHexString(), clue2.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("终点站")
                        .contentMap(Map.of(roleA.toHexString(), "警察将在终点站等候，时间不多了。",
                                roleC.toHexString(), "你掌握了所有证据，是时候摊牌了。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("午夜列车").description("午夜行驶的列车上，一桩走私案与失踪事件交织在一起。")
                .difficulty(ScriptDifficulty.NORMAL).playerCount(3).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 5. 红酒庄园 ───────────────────────────────────────────────────────────
    private Script buildScript5() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId roleD = new ObjectId();
        ObjectId roleE = new ObjectId();

        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();
        ObjectId clue4 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("庄主白峰").avatar("").isNpc(false)
                        .secret("我在酒中下了安眠药").locationTag("酒窖").selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("酿酒师傅张老").avatar("").isNpc(false)
                        .secret("我知道庄主的真实配方被偷了").locationTag("酿酒房").selfClueIds(List.of(clue2.toHexString())).searchPower(2).build(),
                Role.builder().id(roleC).name("品酒师顾美").avatar("").isNpc(false)
                        .secret("我受雇于竞争对手").locationTag("品鉴室").selfClueIds(List.of(clue3.toHexString())).searchPower(3).build(),
                Role.builder().id(roleD).name("庄主之子白晨").avatar("").isNpc(false)
                        .secret("我想卖掉庄园").locationTag("客厅").selfClueIds(List.of()).searchPower(2).build(),
                Role.builder().id(roleE).name("神秘买家").avatar("").isNpc(true)
                        .prompt("你是来收购庄园的商人，表面友善实则居心叵测")
                        .secret("我是竞争庄园派来的间谍").locationTag("花园").selfClueIds(List.of(clue4.toHexString())).searchPower(2).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("空药瓶").type(ClueType.TEXT)
                        .content("酒窖角落发现安眠药空瓶，品牌与庄主常用药品相同。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("酒窖")).build(),
                Clue.builder().id(clue2).title("被盗配方").type(ClueType.TEXT)
                        .content("保险柜被撬，核心酿酒配方不翼而飞，但门锁完好。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("酿酒房")).build(),
                Clue.builder().id(clue3).title("竞争对手委托书").type(ClueType.TEXT)
                        .content("委托书显示顾美受雇于竞争酒庄，任务是获取配方。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleC.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("品鉴室")).build(),
                Clue.builder().id(clue4).title("买家真实身份证").type(ClueType.TEXT)
                        .content("证件显示买家真名为竞争酒庄的市场总监。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleE.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("花园")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("品酒宴会")
                        .contentMap(Map.of(roleA.toHexString(), "宴会上有几位客人行为异常。",
                                roleC.toHexString(), "你需要在宴会上找机会接近酿酒配方。"))
                        .unlockClueIds(List.of(clue1.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("配方失窃")
                        .contentMap(Map.of(roleB.toHexString(), "你发现配方被盗，情况紧急。",
                                roleE.toHexString(), "任务完成了一半，但风险也在增加。"))
                        .unlockClueIds(List.of(clue2.toHexString(), clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("间谍落网")
                        .contentMap(Map.of(roleA.toHexString(), "真相大白，间谍身份暴露。",
                                roleD.toHexString(), "你需要决定庄园的未来。"))
                        .unlockClueIds(List.of(clue4.toHexString())).build()
        );

        return Script.builder().title("红酒庄园").description("百年酒庄的一场品酒宴，竟成了间谍与配方争夺的舞台。")
                .difficulty(ScriptDifficulty.HARD).playerCount(5).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 6. 雪夜凶案 ───────────────────────────────────────────────────────────
    private Script buildScript6() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("旅馆老板冯强").avatar("").isNpc(false)
                        .secret("旅馆的地下室藏有秘密").locationTag("前台").selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("大雪被困游客刘芳").avatar("").isNpc(false)
                        .secret("我认识死者，我们有过节").locationTag("客房").selfClueIds(List.of(clue2.toHexString())).searchPower(2).build(),
                Role.builder().id(roleC).name("退休警察陈叔").avatar("").isNpc(false)
                        .secret("我此行是为了追踪逃犯").locationTag("餐厅").selfClueIds(List.of()).searchPower(4).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("地下室钥匙").type(ClueType.TEXT)
                        .content("老式铁钥匙，编号与旅馆登记系统不符，像是私配的。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("前台")).build(),
                Clue.builder().id(clue2).title("死者遗物").type(ClueType.TEXT)
                        .content("死者皮夹内有一张照片，是刘芳与另一人的合影，背面写着\"永不原谅\"。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("客房")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("暴雪封山")
                        .contentMap(Map.of(roleA.toHexString(), "所有人都被困在旅馆，其中一位客人死亡。",
                                roleC.toHexString(), "你职业本能告诉你这不是意外。"))
                        .unlockClueIds(List.of(clue1.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("凶手就在身边")
                        .contentMap(Map.of(roleB.toHexString(), "你与死者的关系被发现了。",
                                roleC.toHexString(), "逃犯的线索与此案有交叉。"))
                        .unlockClueIds(List.of(clue2.toHexString())).build()
        );

        return Script.builder().title("雪夜凶案").description("大雪封山，孤立无援，旅馆里的每个人都可能是凶手。")
                .difficulty(ScriptDifficulty.EASY).playerCount(3).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 7. 戏班魅影 ───────────────────────────────────────────────────────────
    private Script buildScript7() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId roleD = new ObjectId();
        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("班主鲁大").avatar("").isNpc(false)
                        .secret("我用戏班洗黑钱").locationTag("后台").selfClueIds(List.of(clue1.toHexString())).searchPower(3).build(),
                Role.builder().id(roleB).name("花旦秋月").avatar("").isNpc(false)
                        .secret("我掌握班主洗钱的账本").locationTag("化妆间").selfClueIds(List.of(clue2.toHexString())).searchPower(2).build(),
                Role.builder().id(roleC).name("武生铁虎").avatar("").isNpc(false)
                        .secret("我被班主威胁了多年").locationTag("演武厅").selfClueIds(List.of()).searchPower(3).build(),
                Role.builder().id(roleD).name("神秘观众").avatar("").isNpc(true)
                        .prompt("你是专程来取回账本的黑帮成员，需要不动声色")
                        .secret("我是黑帮派来销毁证据的").locationTag("观众席").selfClueIds(List.of(clue3.toHexString())).searchPower(2).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("账本").type(ClueType.TEXT)
                        .content("账本记录了三年来的洗钱流水，金额触目惊心。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleB.toHexString()))
                        .locationTag(List.of("后台")).build(),
                Clue.builder().id(clue2).title("威胁信").type(ClueType.TEXT)
                        .content("信中要求秋月交出账本，否则后果自负，落款是匿名的。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("化妆间")).build(),
                Clue.builder().id(clue3).title("黑帮联络卡").type(ClueType.TEXT)
                        .content("卡片上只有一个电话号码，查询后是某黑帮头目的联系方式。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleD.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("观众席")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("开场好戏")
                        .contentMap(Map.of(roleA.toHexString(), "你听说账本可能被人拿走了，心里发慌。",
                                roleB.toHexString(), "你决定今晚用账本换取自由。"))
                        .unlockClueIds(List.of(clue2.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("幕后交易")
                        .contentMap(Map.of(roleD.toHexString(), "你需要在散场前拿到账本。",
                                roleC.toHexString(), "多年的愤怒让你决定今晚终结这一切。"))
                        .unlockClueIds(List.of(clue1.toHexString(), clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("大幕落下")
                        .contentMap(Map.of(roleA.toHexString(), "所有人的目的都暴露了，逃无可逃。",
                                roleB.toHexString(), "你的命运取决于这最后一幕。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("戏班魅影").description("百年戏班的最后一场演出，台上台下都是生死博弈。")
                .difficulty(ScriptDifficulty.HARD).playerCount(4).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 8. 实验室危机 ─────────────────────────────────────────────────────────
    private Script buildScript8() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("首席研究员方博").avatar("").isNpc(false)
                        .secret("我知道是谁泄露了研究数据").locationTag("实验室A区").selfClueIds(List.of(clue1.toHexString())).searchPower(4).build(),
                Role.builder().id(roleB).name("实习生宋雨").avatar("").isNpc(false)
                        .secret("我被竞争公司收买了").locationTag("资料室").selfClueIds(List.of(clue2.toHexString())).searchPower(2).build(),
                Role.builder().id(roleC).name("安保队长程刚").avatar("").isNpc(false)
                        .secret("我故意让某人进入了机密区域").locationTag("监控室").selfClueIds(List.of(clue3.toHexString())).searchPower(3).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("数据泄露日志").type(ClueType.TEXT)
                        .content("服务器日志显示，核心数据在凌晨2点被访问并复制。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("实验室A区")).build(),
                Clue.builder().id(clue2).title("加密U盘").type(ClueType.TEXT)
                        .content("U盘内含有最新研究成果，是宋雨准备交付给竞争公司的证据。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("资料室")).build(),
                Clue.builder().id(clue3).title("门禁异常记录").type(ClueType.TEXT)
                        .content("记录显示有人在案发当晚用程刚的权限卡进入了机密区域。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleC.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("监控室")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("数据失窃")
                        .contentMap(Map.of(roleA.toHexString(), "公司核心研究数据被盗，你是负责人。",
                                roleC.toHexString(), "你需要抹去那晚的门禁记录。"))
                        .unlockClueIds(List.of(clue1.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("内鬼追踪")
                        .contentMap(Map.of(roleB.toHexString(), "你被怀疑了，需要转移视线。",
                                roleA.toHexString(), "证据越来越指向内部人员。"))
                        .unlockClueIds(List.of(clue2.toHexString(), clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("真相大白")
                        .contentMap(Map.of(roleA.toHexString(), "所有内鬼浮出水面。",
                                roleB.toHexString(), "是否坦白，是你最后的选择。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("实验室危机").description("高科技实验室内，一场精心策划的数据窃取阴谋正在上演。")
                .difficulty(ScriptDifficulty.NORMAL).playerCount(3).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 9. 幽灵船 ─────────────────────────────────────────────────────────────
    private Script buildScript9() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId roleD = new ObjectId();
        ObjectId roleE = new ObjectId();
        ObjectId roleF = new ObjectId();

        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();
        ObjectId clue4 = new ObjectId();
        ObjectId clue5 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("船长孙海").avatar("").isNpc(false)
                        .secret("我知道这艘船曾经发生过什么").locationTag("驾驶舱").selfClueIds(List.of(clue1.toHexString())).searchPower(4).build(),
                Role.builder().id(roleB).name("考古学家柳研").avatar("").isNpc(false)
                        .secret("我来寻找沉船中的文物").locationTag("甲板").selfClueIds(List.of(clue2.toHexString())).searchPower(3).build(),
                Role.builder().id(roleC).name("潜水员古力").avatar("").isNpc(false)
                        .secret("我在水下发现了不该发现的东西").locationTag("潜水室").selfClueIds(List.of(clue3.toHexString())).searchPower(3).build(),
                Role.builder().id(roleD).name("历史学家刘博").avatar("").isNpc(false)
                        .secret("我掌握着这片海域的历史档案").locationTag("资料室").selfClueIds(List.of(clue4.toHexString())).searchPower(2).build(),
                Role.builder().id(roleE).name("神秘富豪").avatar("").isNpc(false)
                        .secret("这次探险是我策划的，有隐藏目的").locationTag("豪华舱").selfClueIds(List.of()).searchPower(3).build(),
                Role.builder().id(roleF).name("幽灵水手").avatar("").isNpc(true)
                        .prompt("你是百年前沉船幸存者的灵魂，用谜语和暗示引导玩家")
                        .secret("我是守护沉船秘密的存在").locationTag("底舱").selfClueIds(List.of(clue5.toHexString())).searchPower(1).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("航海图").type(ClueType.TEXT)
                        .content("百年前的航海图，标注了一个神秘坐标，与此次打捞地点完全一致。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("驾驶舱")).build(),
                Clue.builder().id(clue2).title("沉船文物").type(ClueType.TEXT)
                        .content("文物上刻有古代贸易公司的徽记，价值连城。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleE.toHexString()))
                        .locationTag(List.of("甲板")).build(),
                Clue.builder().id(clue3).title("水下录像").type(ClueType.TEXT)
                        .content("录像显示沉船内有一个被人工封闭的舱室，里面似乎有东西在动。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleC.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("潜水室")).build(),
                Clue.builder().id(clue4).title("历史档案").type(ClueType.TEXT)
                        .content("档案记载，沉船载有一批神秘货物，船员全部失踪，无人生还。")
                        .isInitialHidden(false).searchableRoleIds(List.of(roleD.toHexString(), roleB.toHexString()))
                        .locationTag(List.of("资料室")).build(),
                Clue.builder().id(clue5).title("古老诅咒书").type(ClueType.TEXT)
                        .content("用古老文字写成，翻译后是一段警告：不要打开那个舱室。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleF.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("底舱")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("抵达遗址")
                        .contentMap(Map.of(roleA.toHexString(), "船只抵达目标海域，设备开始出现异常。",
                                roleF.toHexString(), "你感觉到他们来了，开始显现。"))
                        .unlockClueIds(List.of(clue4.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("水下发现")
                        .contentMap(Map.of(roleC.toHexString(), "你在水下看到了令人恐惧的东西。",
                                roleE.toHexString(), "真正的目的即将实现，但代价可能超出预期。"))
                        .unlockClueIds(List.of(clue1.toHexString(), clue2.toHexString(), clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("幽灵警告")
                        .contentMap(Map.of(roleF.toHexString(), "警告已经发出，是否有人听进去是他们的选择。",
                                roleD.toHexString(), "历史上记载的悲剧似乎要重演。"))
                        .unlockClueIds(List.of(clue5.toHexString())).build(),
                ScriptStage.builder().stageNumber(4).stageTitle("最终抉择")
                        .contentMap(Map.of(roleE.toHexString(), "打开舱室还是永远封存，你来决定。",
                                roleA.toHexString(), "作为船长，你有责任保护所有人。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("幽灵船").description("神秘的百年沉船打捞行动，竟惊醒了沉睡深海的秘密。")
                .difficulty(ScriptDifficulty.EXPERT).playerCount(6).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }

    // ─── 10. 都市传说 ──────────────────────────────────────────────────────────
    private Script buildScript10() {
        ObjectId roleA = new ObjectId();
        ObjectId roleB = new ObjectId();
        ObjectId roleC = new ObjectId();
        ObjectId roleD = new ObjectId();

        ObjectId clue1 = new ObjectId();
        ObjectId clue2 = new ObjectId();
        ObjectId clue3 = new ObjectId();

        List<Role> roles = List.of(
                Role.builder().id(roleA).name("网红博主小鱼").avatar("").isNpc(false)
                        .secret("我炮制了那个都市传说赚流量").locationTag("直播间").selfClueIds(List.of(clue1.toHexString())).searchPower(2).build(),
                Role.builder().id(roleB).name("灵异爱好者老陈").avatar("").isNpc(false)
                        .secret("传说中的地点我去过，不是鬼").locationTag("资料室").selfClueIds(List.of(clue2.toHexString())).searchPower(3).build(),
                Role.builder().id(roleC).name("警察小王").avatar("").isNpc(false)
                        .secret("那栋楼确实有真实犯罪案底").locationTag("办公室").selfClueIds(List.of(clue3.toHexString())).searchPower(4).build(),
                Role.builder().id(roleD).name("楼管大爷").avatar("").isNpc(true)
                        .prompt("你是知情者，总是用隐晦的话暗示真相，从不直说")
                        .secret("我亲眼目睹了当年发生的事").locationTag("门卫室").selfClueIds(List.of()).searchPower(1).build()
        );

        List<Clue> clues = List.of(
                Clue.builder().id(clue1).title("直播策划案").type(ClueType.TEXT)
                        .content("策划案显示小鱼蓄意策划都市传说内容，目的是涨粉变现。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleA.toHexString(), roleC.toHexString()))
                        .locationTag(List.of("直播间")).build(),
                Clue.builder().id(clue2).title("实地探访照片").type(ClueType.TEXT)
                        .content("照片显示那栋楼确实存在异常，但是人为制造的布景。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleB.toHexString(), roleA.toHexString()))
                        .locationTag(List.of("资料室")).build(),
                Clue.builder().id(clue3).title("案件档案").type(ClueType.TEXT)
                        .content("档案显示10年前该楼确实发生过一起命案，从未公开。")
                        .isInitialHidden(true).searchableRoleIds(List.of(roleC.toHexString(), roleD.toHexString()))
                        .locationTag(List.of("办公室")).build()
        );

        List<ScriptStage> stages = List.of(
                ScriptStage.builder().stageNumber(1).stageTitle("传说流传")
                        .contentMap(Map.of(roleA.toHexString(), "你的传说视频爆火，但引来了不该引来的注意。",
                                roleD.toHexString(), "看到视频的你眉头紧皱，某段往事被勾起。"))
                        .unlockClueIds(List.of(clue1.toHexString())).build(),
                ScriptStage.builder().stageNumber(2).stageTitle("真假难辨")
                        .contentMap(Map.of(roleB.toHexString(), "你发现了传说背后的人为痕迹。",
                                roleC.toHexString(), "案件档案与这个地点产生了关联。"))
                        .unlockClueIds(List.of(clue2.toHexString(), clue3.toHexString())).build(),
                ScriptStage.builder().stageNumber(3).stageTitle("真相揭露")
                        .contentMap(Map.of(roleA.toHexString(), "流量游戏变成了真实危险。",
                                roleD.toHexString(), "是时候说出你隐藏多年的秘密了。"))
                        .unlockClueIds(List.of()).build()
        );

        return Script.builder().title("都市传说").description("一个网红炮制的都市传说，却意外触碰了真实的黑暗往事。")
                .difficulty(ScriptDifficulty.NORMAL).playerCount(4).coverImage("")
                .roles(roles).clues(clues).stages(stages).version(1).build();
    }
}
