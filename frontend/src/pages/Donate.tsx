import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken } from "../api/client";
import { Nav } from "./Nav";

/** 预设金额（元） */
const PRESETS = [5, 10, 20, 50, 100, 200];

interface DonationItem {
  id: number;
  donor: string;
  amountCents: number;
  channel: string;
  createdAt: string;
}

interface DonationList {
  totalCents: number;
  page: number;
  totalPages: number;
  items: DonationItem[];
}

/** 分 → 元（千分位 + 两位小数） */
function fmtYuan(cents: number): string {
  return (cents / 100).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/**
 * 捐助拾客 Shike（公开页，免登录）：
 * 收到的捐助资金用于开源项目的开发开销。
 * 金额 5/10/20/50/100/200 或自定义；捐赠人（账号）选填；支付宝/微信支付（演示环境模拟，不真实扣款）。
 */
export default function Donate() {
  const navigate = useNavigate();
  const [preset, setPreset] = useState<number | null>(50);
  const [custom, setCustom] = useState("");
  const [donor, setDonor] = useState("");
  const [paying, setPaying] = useState(false);
  const [msg, setMsg] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [list, setList] = useState<DonationList | null>(null);
  const [page, setPage] = useState(0);
  /** 收款码弹窗：null 关闭；alipay/wechat 弹出对应收款码 */
  const [qrChannel, setQrChannel] = useState<"alipay" | "wechat" | null>(null);
  /** 弹窗打开时锁定的金额（分），防止弹窗打开期间改金额导致记录与展示不一致 */
  const [qrCents, setQrCents] = useState(0);

  const amountYuan = preset !== null ? preset : parseFloat(custom);

  const load = async (p: number) => {
    try {
      const data = await api<DonationList>(`/donations?page=${p}`);
      setList(data);
      setPage(data.page);
    } catch {
      // 记录加载失败不打断捐助流程
    }
  };

  useEffect(() => {
    load(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 点击支付按钮：校验金额后弹出对应收款码（真实扫码付款） */
  const pay = (channel: "alipay" | "wechat") => {
    if (
      !amountYuan ||
      isNaN(amountYuan) ||
      amountYuan < 1 ||
      amountYuan > 100000
    ) {
      setMsg({ type: "error", text: "捐助金额需在 1 ~ 100000 元之间" });
      return;
    }
    setMsg(null);
    setQrCents(Math.round(amountYuan * 100));
    setQrChannel(channel);
  };

  /** 扫码付款完成后点击「我已完成支付」：记录入库 */
  const confirmDonate = async () => {
    const channel = qrChannel;
    if (!channel) return;
    setPaying(true);
    setMsg(null);
    try {
      await api("/donations", {
        method: "POST",
        body: JSON.stringify({
          amountCents: qrCents,
          channel,
          donor: donor.trim(),
        }),
      });
      setQrChannel(null);
      setMsg({
        type: "success",
        text: "捐助成功！感谢您对拾客 Shike 开源项目的支持 ❤",
      });
      load(page);
    } catch (e) {
      setMsg({ type: "error", text: (e as Error).message });
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="page">
      <Nav
        current="donate"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="donate-page">
        <div className="donate-header">
          <img src={`${import.meta.env.BASE_URL}logo.svg`} alt="拾客 Shike" />
          <h1>捐助拾客 Shike</h1>
        </div>
        <p className="donate-desc">收到的捐助资金用于开源项目的开发开销</p>

        <div className="card donate-card">
          <div className="donate-grid">
            {PRESETS.map((v) => (
              <button
                key={v}
                className={`donate-amount${preset === v ? " selected" : ""}`}
                onClick={() => {
                  setPreset(v);
                  setCustom("");
                }}
              >
                ￥{v}
              </button>
            ))}
          </div>
          <input
            className="donate-input"
            placeholder="自定义金额（元）"
            value={custom}
            onChange={(e) => {
              setCustom(e.target.value.replace(/[^\d.]/g, ""));
              setPreset(null);
            }}
          />
          <input
            className="donate-input"
            placeholder="捐赠人（账号，选填）"
            maxLength={64}
            value={donor}
            onChange={(e) => setDonor(e.target.value)}
          />
          <div className="donate-pay-row">
            <button
              className="donate-pay alipay"
              disabled={paying}
              onClick={() => pay("alipay")}
            >
              支付宝
            </button>
            <button
              className="donate-pay wechat"
              disabled={paying}
              onClick={() => pay("wechat")}
            >
              微信支付
            </button>
          </div>
          {msg && (
            <div
              className={`msg ${msg.type === "success" ? "success" : "error"}`}
            >
              {msg.text}
            </div>
          )}
        </div>

        <h2 className="donate-records-title">捐助记录</h2>
        {list && (
          <>
            <div className="donate-summary">
              <span>已收到捐助：￥{fmtYuan(list.totalCents)}</span>
              <span className="donate-pager">
                <button disabled={page <= 0} onClick={() => load(page - 1)}>
                  〈 上一页
                </button>
                <span style={{ margin: "0 8px" }}>
                  第 {page + 1} 页，共 {Math.max(1, list.totalPages)} 页
                </span>
                <button
                  disabled={page + 1 >= list.totalPages}
                  onClick={() => load(page + 1)}
                >
                  下一页 〉
                </button>
              </span>
            </div>
            <div className="donate-list">
              {list.items.length === 0 ? (
                <div className="msg">暂无捐助记录，成为第一位捐助者吧 ❤</div>
              ) : (
                list.items.map((item) => (
                  <div className="donate-item" key={item.id}>
                    <span className="heart">❤</span>
                    <span className="donor">{item.donor}</span>
                    <span>捐助了</span>
                    <span className="amount">
                      ￥{fmtYuan(item.amountCents)}
                    </span>
                    <span className="time">
                      {item.createdAt
                        ? item.createdAt.replace("T", " ").slice(0, 16)
                        : ""}
                    </span>
                  </div>
                ))
              )}
            </div>
          </>
        )}
      </div>

      {/* 收款码弹窗：支付宝=alipay.jpg / 微信=wechat-pay.jpg，扫码真实付款后点「我已完成支付」入库 */}
      {qrChannel && (
        <div className="modal-mask" onClick={() => setQrChannel(null)}>
          <div
            className="modal donate-qr-modal"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <h3>{qrChannel === "alipay" ? "支付宝" : "微信支付"}扫码捐助</h3>
            <p className="donate-qr-amount">
              捐助金额：<strong>￥{fmtYuan(qrCents)}</strong>
            </p>
            <img
              className="donate-qr-img"
              src={`${import.meta.env.BASE_URL}${
                qrChannel === "alipay" ? "alipay.jpg" : "wechat-pay.jpg"
              }`}
              alt={qrChannel === "alipay" ? "支付宝收款码" : "微信支付收款码"}
            />
            <p className="donate-qr-tip">
              请打开{qrChannel === "alipay" ? "支付宝" : "微信"}
              扫一扫，扫描上方收款码完成付款
            </p>
            <div className="dialog-actions">
              <button
                className="btn btn-sm btn-default"
                onClick={() => setQrChannel(null)}
              >
                取消
              </button>
              <button
                className="btn btn-sm"
                disabled={paying}
                onClick={confirmDonate}
              >
                我已完成支付
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
