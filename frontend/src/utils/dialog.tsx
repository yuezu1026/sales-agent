import { useEffect, useState } from "react";
import { createRoot, Root } from "react-dom/client";

/**
 * Toast 风格的应用内弹层，替代原生 window.confirm / window.prompt。
 * 原生对话框在 VS Code 内置浏览器中标题固定为 "Code"（宿主标题），无法控制；
 * 改用应用内卡片后标题可控（如"操作确认"），且样式与产品一致。
 */

interface ConfirmOptions {
  title?: string;
  danger?: boolean;
  confirmText?: string;
  cancelText?: string;
}

interface PromptOptions {
  title?: string;
  placeholder?: string;
  defaultValue?: string;
}

let root: Root | null = null;

/** 卸载当前弹层（若有） */
function clearDialog() {
  if (root) {
    root.unmount();
    root = null;
  }
}

/** 确认弹层：resolve(true) 确认 / resolve(false) 取消（点遮罩、Esc、取消按钮） */
export function confirmDialog(
  text: string,
  opts: ConfirmOptions = {},
): Promise<boolean> {
  const {
    title = "操作确认",
    danger = false,
    confirmText = "确定",
    cancelText = "取消",
  } = opts;
  return new Promise((resolve) => {
    clearDialog();
    const host = document.createElement("div");
    document.body.appendChild(host);
    const close = (result: boolean) => {
      window.removeEventListener("keydown", esc);
      clearDialog();
      host.remove();
      resolve(result);
    };
    const esc = (e: KeyboardEvent) => {
      if (e.key === "Escape") close(false);
    };
    window.addEventListener("keydown", esc);
    root = createRoot(host);
    root.render(
      <div className="dialog-mask" onClick={() => close(false)}>
        <div
          className="dialog-card"
          onClick={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
        >
          <h3 className={`dialog-title${danger ? " dialog-title-danger" : ""}`}>
            {title}
          </h3>
          <div className="dialog-body">{text}</div>
          <div className="dialog-actions">
            <button
              className="btn btn-sm btn-default"
              onClick={() => close(false)}
              autoFocus
            >
              {cancelText}
            </button>
            <button
              className={`btn btn-sm${danger ? " btn-danger" : ""}`}
              onClick={() => close(true)}
            >
              {confirmText}
            </button>
          </div>
        </div>
      </div>,
    );
  });
}

/** 输入弹层（替代 window.prompt）：resolve(输入值) / resolve(null) 取消 */
export function promptDialog(
  text: string,
  opts: PromptOptions = {},
): Promise<string | null> {
  const { title = "请输入", placeholder = "", defaultValue = "" } = opts;
  return new Promise((resolve) => {
    clearDialog();
    const host = document.createElement("div");
    document.body.appendChild(host);
    const close = (value: string | null) => {
      clearDialog();
      host.remove();
      resolve(value);
    };
    root = createRoot(host);
    root.render(
      <PromptBody
        text={text}
        title={title}
        placeholder={placeholder}
        defaultValue={defaultValue}
        onClose={close}
      />,
    );
  });
}

function PromptBody({
  text,
  title,
  placeholder,
  defaultValue,
  onClose,
}: {
  text: string;
  title: string;
  placeholder: string;
  defaultValue: string;
  onClose: (v: string | null) => void;
}) {
  const [val, setVal] = useState(defaultValue);
  const submit = () => {
    const v = val.trim();
    onClose(v ? v : null);
  };
  useEffect(() => {
    const esc = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose(null);
    };
    window.addEventListener("keydown", esc);
    return () => window.removeEventListener("keydown", esc);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <div className="dialog-mask" onClick={() => onClose(null)}>
      <div
        className="dialog-card"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <h3 className="dialog-title">{title}</h3>
        <div className="dialog-body">
          <div className="dialog-text">{text}</div>
          <input
            className="input"
            autoFocus
            value={val}
            placeholder={placeholder}
            onChange={(e) => setVal(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") submit();
            }}
          />
        </div>
        <div className="dialog-actions">
          <button
            className="btn btn-sm btn-default"
            onClick={() => onClose(null)}
          >
            取消
          </button>
          <button className="btn btn-sm" onClick={submit}>
            确定
          </button>
        </div>
      </div>
    </div>
  );
}
