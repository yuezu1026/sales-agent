import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { clearToken } from "../api/client";
import { Nav } from "./Nav";

interface FaqItem {
  q: string;
  a: string;
}

const FAQS: FaqItem[] = [
  {
    q: "为什么邮件发送失败？",
    a: "常见原因：① 系统设置中 SMTP 授权码错误或为空；② SMTP 服务器/端口配置不对（QQ 邮箱用 465 SSL，部分服务商用 587 STARTTLS）；③ 客户邮箱为空或格式错误。发送失败后可在「客户详情 → 发送记录」中查看具体失败原因，修复配置后点「↻ 重试」。",
  },
  {
    q: "AI 生成邮件/回复报错或没反应？",
    a: "先到「系统设置」确认 ai.api_key 已填写且有效、ai.base_url 与 ai.model_name 匹配（如 https://api.deepseek.com + deepseek-chat）。填写后保存立即生效，无需重启。若 Key 欠费或限额用尽也会报错。",
  },
  {
    q: "每日发送上限怎么调整？",
    a: "在「系统设置」中修改 mail.daily_limit（默认 50 封）。该限制模拟人工发信节奏，防止账号被判定为垃圾邮件。今日已发送数达到上限后需等次日 0 点重置。",
  },
  {
    q: "如何注册使用？",
    a: "点击登录页的“免费注册，立即开始”，填写用户名（3-32 位字母/数字/下划线）、密码（至少 8 位）即可完成注册。注册即创建独立工作空间（租户），数据与其他用户完全隔离。显示名与公司名可后续在个人设置中修改。",
  },
  {
    q: "退订链接怎么配置？",
    a: "在「系统设置」填写 mail.unsubscribe_url，例如 https://your-domain.com/unsub。发送邮件时正文末尾会自动追加退订链接，支持 {email} 占位符自动替换收件人邮箱（不填则自动拼接 ?email=xxx）。留空则不追加。营销邮件建议必须配置，符合合规要求并降低退信率。",
  },
  {
    q: "邮件正文支持哪些变量？",
    a: "发送时草稿的 subject/body 中的 {companyName} {contactName} {contactEmail} {phone} {contactPhone} {gender} {industry} {region} {scale} {website} {address} {date} {year} 等占位符会自动替换为对应客户的实际字段值（空字段替换为空）。例如正文写「尊敬的 {contactName}」，发送给张三时会自动变成「尊敬的 张三」。可先写占位符再对每个客户复用同一封草稿。",
  },
  {
    q: "我的数据存在哪里？安全吗？",
    a: "系统为本地部署，客户资料、邮件、配置均存储在本机 PostgreSQL 数据库中，不经过第三方服务器。AI 调用云端大模型 API 时仅发送生成所需的文本内容（如画像摘要、邮件草稿正文）。SMTP 授权码与 AI Key 在数据库中 AES 加密存储。",
  },
  {
    q: "收件箱为什么收不到客户回复？",
    a: "收件箱通过 IMAP 协议拉取发件邮箱的来信，需在「系统设置」配置 smtp.host/port/username/password（收信走 IMAP 同账号）。确认邮箱开启了 IMAP 服务（QQ 邮箱需在设置中开启）。",
  },
  {
    q: "AI 建议的内容可以直接发送吗？",
    a: "不可以，也不能。系统遵循「人机协同」原则：AI 只生成建议内容，所有外发动作（邮件发送、微信记录确认）都必须由你人工预览、确认后才会执行。这是产品的核心红线设计，防止机械化触达导致客户反感。",
  },
];

/** 快速开始步骤 */
const QUICK_STEPS = [
  {
    step: "01",
    title: "登录系统",
    desc: "使用管理员账号登录（注册后即为租户管理员）。注册用户各自拥有独立数据空间，互不可见。",
    target: "登录页",
  },
  {
    step: "02",
    title: "完成系统配置",
    desc: "在「系统设置」填写 AI 模型 Key（必填）、SMTP 发件邮箱（发邮件必填）、退订链接（建议）。保存立即生效。",
    target: "系统设置",
  },
  {
    step: "03",
    title: "导入历史客户（可选）",
    desc: "在「客户画像」导入企业历史客户 CSV，系统自动向量化并构建相似画像，后续潜客挖掘/邮件生成可参考。",
    target: "客户画像",
  },
  {
    step: "04",
    title: "开始获客闭环",
    desc: "「潜客挖掘」找线索 →「客户管理」跟进与打标 → AI 生成邮件 → 人工确认 → SMTP 发送 → 收件箱跟进回复。",
    target: "客户管理",
  },
];

/** 模块使用指南 */
const MODULES = [
  {
    icon: "🎯",
    title: "潜客挖掘",
    path: "/prospect",
    desc: "输入行业 / 地区 / 关键词等条件，系统调用数据源 API 挖掘潜在客户，并结合历史画像相似度排序，人工筛选后一键入库。",
    tips: ["挖掘结果需人工确认才入库", "入库时自动按公司名去重"],
  },
  {
    icon: "🧬",
    title: "客户画像",
    path: "/profile",
    desc: "导入历史成交客户 CSV 生成画像库；对潜客计算与历史客户画像的相似度得分（0-100 分），分越高越接近优质客户特征。",
    tips: ["支持一键重算全部画像", "画像向量存本地，无隐私外泄"],
  },
  {
    icon: "👥",
    title: "客户管理",
    path: "/customers",
    desc: "客户列表支持搜索 / 筛选 / 分页 / 新增 / 编辑 / 删除 / CSV 导入导出。状态流转：新线索 → 已触达 → 有意向 → 已转化 / 无效。",
    tips: [
      "点击「详情」查看跟进记录、邮件草稿、发送记录",
      "支持微信沟通工作台",
    ],
  },
  {
    icon: "✉️",
    title: "邮件触达",
    path: "/customers",
    desc: "客户详情中 AI 生成个性化邮件 → 保存草稿 → 标记待发（confirmed）→「✉ 发送」SMTP 投递。发送记录可查看状态、失败原因并重试。",
    tips: [
      "发送前需人工确认（人机协同红线）",
      "每日发送数受 mail.daily_limit 限制",
      "正文自动追加退订链接（已配置时）",
    ],
  },
  {
    icon: "💬",
    title: "微信工作台",
    path: "/customers",
    desc: "记录客户微信沟通消息。AI 可基于对话生成回复建议，人工确认后记录为已发，自动标记 AI 辅助徽标。",
    tips: ["AI 只建议，人工确认后才落库", "支持记录客户消息与已发消息"],
  },
  {
    icon: "📥",
    title: "收件箱",
    path: "/inbox",
    desc: "通过 IMAP 自动拉取客户回复邮件，与发送记录形成闭环，查看客户对邮件的真实反馈。",
    tips: ["需邮箱开启 IMAP 服务", "未读邮件有高亮标记"],
  },
  {
    icon: "🗂️",
    title: "草稿箱",
    path: "/drafts",
    desc: "跨客户统一管理所有邮件草稿，支持搜索、状态筛选（草稿 / 待发 / 已发送）、标记待发、发送与删除。",
    tips: ["已发送（sent）草稿不可再编辑", "待发（confirmed）草稿可直接发送"],
  },
];

/** 帮助中心：快速开始 + 模块指南 + 常见问题 */
export default function Help() {
  const navigate = useNavigate();
  const [open, setOpen] = useState<number | null>(null);

  return (
    <div>
      <Nav
        current="help"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="container">
        <h2>❓ 帮助中心</h2>

        {/* 快速开始 */}
        <div className="card" style={{ marginBottom: 20 }}>
          <h3 style={{ marginTop: 0 }}>🚀 快速开始</h3>
          <div className="help-steps">
            {QUICK_STEPS.map((s) => (
              <div className="help-step" key={s.step}>
                <div className="help-step-num">{s.step}</div>
                <div className="help-step-body">
                  <div className="help-step-title">
                    {s.title}
                    <span className="help-step-target">{s.target}</span>
                  </div>
                  <div className="help-step-desc">{s.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 模块指南 */}
        <div className="card" style={{ marginBottom: 20 }}>
          <h3 style={{ marginTop: 0 }}>📚 模块使用指南</h3>
          <div className="help-modules">
            {MODULES.map((m) => (
              <div className="help-module" key={m.title}>
                <div className="help-module-icon">{m.icon}</div>
                <div className="help-module-body">
                  <div className="help-module-title">{m.title}</div>
                  <div className="help-module-desc">{m.desc}</div>
                  <div className="help-module-tips">
                    {m.tips.map((t) => (
                      <span className="help-tip" key={t}>
                        {t}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* FAQ */}
        <div className="card" style={{ marginBottom: 20 }}>
          <h3 style={{ marginTop: 0 }}>💡 常见问题</h3>
          {FAQS.map((f, i) => (
            <div className="help-faq" key={f.q}>
              <button
                className="help-faq-q"
                onClick={() => setOpen(open === i ? null : i)}
              >
                <span>{f.q}</span>
                <span className={`help-faq-arrow${open === i ? " open" : ""}`}>
                  ▾
                </span>
              </button>
              {open === i && <div className="help-faq-a">{f.a}</div>}
            </div>
          ))}
        </div>

        {/* 关于 */}
        <div className="card">
          <h3 style={{ marginTop: 0 }}>ℹ️ 关于</h3>
          <div className="help-about">
            <div>
              <b>AI 智能获客助手</b>（AI Sales Agent）—— 端到端 AI 销售智能体，
              实现「发现 → 触达 → 转化」的自动化获客闭环。
            </div>
            <ul style={{ margin: "8px 0 0", paddingLeft: 20 }}>
              <li>部署方式：本地部署（Docker Compose 一键启动）</li>
              <li>数据存储：本地 PostgreSQL，配置与密钥 AES 加密</li>
              <li>
                AI 模型：支持 DeepSeek 等 OpenAI 兼容 API（系统设置中切换）
              </li>
              <li>外发原则：AI 只建议、人确认发送（人机协同）</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
