import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";

/**
 * 退订落地页（免登录）：/unsubscribe?email=xxx
 * M3-2 合规闭环：收件人点击邮件内退订链接后到达此页，调用公开接口
 * GET /api/unsubscribe?email=xxx（幂等），展示退订结果。
 */
export default function Unsubscribe() {
  const [searchParams] = useSearchParams();
  const email = searchParams.get("email") || "";
  const [state, setState] = useState<{
    type: "loading" | "success" | "already" | "invalid" | "error";
    text: string;
  }>({ type: "loading", text: "处理中..." });
  const called = useRef(false);

  useEffect(() => {
    if (called.current) return;
    called.current = true;
    const doUnsubscribe = async () => {
      if (!email || !email.includes("@")) {
        setState({
          type: "invalid",
          text: "退订链接无效，请检查邮件中的退订链接",
        });
        return;
      }
      try {
        const data = await api<{ status: string; message: string }>(
          `/unsubscribe?email=${encodeURIComponent(email)}`,
        );
        if (data.status === "already") {
          setState({ type: "already", text: data.message });
        } else {
          setState({ type: "success", text: data.message });
        }
      } catch (e) {
        setState({ type: "error", text: (e as Error).message });
      }
    };
    doUnsubscribe();
  }, [email]);

  return (
    <div className="auth-page">
      <div className="auth-box card">
        <div className="auth-header">
          <img
            src={`${import.meta.env.BASE_URL}logo.svg`}
            alt="拾客 Shike"
            className="auth-logo"
          />
          <h2 className="auth-title">邮件退订</h2>
        </div>
        {state.type === "loading" ? (
          <div className="msg">正在处理退订请求...</div>
        ) : (
          <div
            className={`msg ${
              state.type === "error" || state.type === "invalid"
                ? "error"
                : "success"
            }`}
          >
            {state.text}
          </div>
        )}
        {(state.type === "success" || state.type === "already") && (
          <div className="msg" style={{ fontSize: 13 }}>
            如误操作想恢复接收邮件，请联系发件方处理。
          </div>
        )}
      </div>
    </div>
  );
}
