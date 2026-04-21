import { useEffect, useMemo, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "";

const STORAGE_KEY = "expiry_user";
const OCR_NAME_OVERRIDES_KEY = "ocr_name_overrides";
const BRAND_NAME = "ExpiryLens";
const BRAND_TAGLINE = "Digital Expiry Tracker";
const BRAND_LOGO_PATH = "/expirylens-logo.svg";

function App() {
  const [ocrNameOverrides, setOcrNameOverrides] = useState(() => {
    const raw = localStorage.getItem(OCR_NAME_OVERRIDES_KEY);
    return raw ? JSON.parse(raw) : {};
  });

  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  });

  const [mode, setMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [products, setProducts] = useState([]);
  const [expiring, setExpiring] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [newProductName, setNewProductName] = useState("");
  const [newExpiryDate, setNewExpiryDate] = useState("");

  const [uploadFile, setUploadFile] = useState(null);
  const [uploadProductName, setUploadProductName] = useState("");
  const [uploadPreview, setUploadPreview] = useState("");

  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState("");
  const [editingExpiry, setEditingExpiry] = useState("");
  const [showBrandLogo, setShowBrandLogo] = useState(true);

  const nearExpiryCount = useMemo(() => expiring.length, [expiring]);

  useEffect(() => {
    if (!user) {
      return;
    }
    loadProducts();
    loadExpiring();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const authEndpoint = mode === "login" ? "/auth/login" : "/auth/register";

  const onAuthSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    const payload = mode === "login"
      ? { email, password }
      : { name, email, password };

    try {
      const res = await fetch(`${API_BASE}${authEndpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || data.error || "Authentication failed");
      }

      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
      setUser(data);
      setPassword("");
      setName("");
      setEmail("");
    } catch (err) {
      setError(err.message || "Authentication failed");
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem(STORAGE_KEY);
    setUser(null);
    setProducts([]);
    setExpiring([]);
    setError("");
  };

  const loadProducts = async (nameOverrides = ocrNameOverrides) => {
    const res = await fetch(`${API_BASE}/product?userId=${user.id}`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || "Failed to load products");
    }
    const withDisplayNameOverrides = data.map((product) => ({
      ...product,
      productName: nameOverrides[product.id] || product.productName,
    }));
    setProducts(withDisplayNameOverrides);
  };

  const loadExpiring = async () => {
    const res = await fetch(`${API_BASE}/product/expiring?userId=${user.id}&days=7`);
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || "Failed to load expiring products");
    }
    setExpiring(data);
  };

  const refreshLists = async (nameOverrides = ocrNameOverrides) => {
    setLoading(true);
    setError("");
    try {
      await Promise.all([loadProducts(nameOverrides), loadExpiring()]);
    } catch (err) {
      setError(err.message || "Failed to refresh");
    } finally {
      setLoading(false);
    }
  };

  const addManualProduct = async (e) => {
    e.preventDefault();
    if (!newProductName || !newExpiryDate) return;

    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/product?userId=${user.id}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ productName: newProductName, expiryDate: newExpiryDate }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Failed to add product");
      }

      setNewProductName("");
      setNewExpiryDate("");
      await refreshLists();
    } catch (err) {
      setError(err.message || "Failed to add product");
    } finally {
      setLoading(false);
    }
  };

  const onFileChange = (e) => {
    const selected = e.target.files?.[0];
    if (!selected) return;
    setUploadFile(selected);
    setUploadPreview(URL.createObjectURL(selected));
  };

  const uploadProductImage = async () => {
    const imageProductName = uploadProductName.trim();
    if (!uploadFile || !imageProductName) {
      return;
    }

    const formData = new FormData();
    formData.append("file", uploadFile);
    formData.append("userId", String(user.id));
    formData.append("productName", imageProductName);

    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/product/upload`, {
        method: "POST",
        body: formData,
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Image scan failed");
      }

      if (data?.id) {
        const nextOverrides = {
          ...ocrNameOverrides,
          [data.id]: imageProductName,
        };
        setOcrNameOverrides(nextOverrides);
        localStorage.setItem(OCR_NAME_OVERRIDES_KEY, JSON.stringify(nextOverrides));
        await refreshLists(nextOverrides);
      } else {
        await refreshLists();
      }

      setUploadFile(null);
      setUploadProductName("");
      setUploadPreview("");
    } catch (err) {
      setError(err.message || "Image scan failed");
    } finally {
      setLoading(false);
    }
  };

  const startEdit = (product) => {
    setEditingId(product.id);
    setEditingName(product.productName);
    setEditingExpiry(product.expiryDate);
  };

  const saveEdit = async (id) => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/product/${id}?userId=${user.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ productName: editingName, expiryDate: editingExpiry }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Failed to update product");
      }

      const nextOverrides = {
        ...ocrNameOverrides,
        [id]: editingName,
      };
      setOcrNameOverrides(nextOverrides);
      localStorage.setItem(OCR_NAME_OVERRIDES_KEY, JSON.stringify(nextOverrides));

      setEditingId(null);
      setEditingName("");
      setEditingExpiry("");
      await refreshLists(nextOverrides);
    } catch (err) {
      setError(err.message || "Failed to update product");
    } finally {
      setLoading(false);
    }
  };

  const deleteProduct = async (id) => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API_BASE}/product/${id}?userId=${user.id}`, {
        method: "DELETE",
      });

      if (!res.ok) {
        const data = await res.json();
        throw new Error(data.message || "Failed to delete product");
      }

      if (ocrNameOverrides[id]) {
        const nextOverrides = { ...ocrNameOverrides };
        delete nextOverrides[id];
        setOcrNameOverrides(nextOverrides);
        localStorage.setItem(OCR_NAME_OVERRIDES_KEY, JSON.stringify(nextOverrides));
        await refreshLists(nextOverrides);
      } else {
        await refreshLists();
      }
    } catch (err) {
      setError(err.message || "Failed to delete product");
    } finally {
      setLoading(false);
    }
  };

  if (!user) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-[#f4f1e8] via-[#eef3ec] to-[#e5ede6] text-[#1f3b33] flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md rounded-3xl bg-[#f9f7f1] border border-[#d0dccf] p-8 shadow-2xl shadow-[#7e8f7a]/15">
          <div className="flex flex-col items-center text-center">
            {showBrandLogo && (
              <img
                src={BRAND_LOGO_PATH}
                alt="ExpiryLens logo"
                className="h-24 w-auto rounded-2xl object-contain"
                onError={() => setShowBrandLogo(false)}
              />
            )}
            <h1 className="mt-4 text-4xl font-bold tracking-tight text-[#2f5f4c]">{BRAND_NAME}</h1>
            <p className="mt-1 text-xs tracking-[0.18em] uppercase text-[#7d6753]">{BRAND_TAGLINE}</p>
          </div>
          <p className="mt-4 text-center text-[#4f675f]">{mode === "login" ? "Login to track your products" : "Create account to start tracking"}</p>

          <form className="mt-6 space-y-4" onSubmit={onAuthSubmit}>
            {mode === "register" && (
              <input
                className="w-full rounded-xl border border-[#c9d8c8] bg-[#ffffff] px-4 py-2.5 outline-none focus:border-[#2f7a62]"
                placeholder="Full name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            )}

            <input
                className="w-full rounded-xl border border-[#c9d8c8] bg-[#ffffff] px-4 py-2.5 outline-none focus:border-[#2f7a62]"
              placeholder="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />

            <input
                className="w-full rounded-xl border border-[#c9d8c8] bg-[#ffffff] px-4 py-2.5 outline-none focus:border-[#2f7a62]"
              placeholder="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />

            <button
                className="w-full rounded-xl bg-[#2f7a62] text-[#f8f4eb] font-semibold py-2.5 hover:bg-[#25654f] transition disabled:opacity-50"
              disabled={loading}
            >
              {loading ? "Please wait..." : mode === "login" ? "Login" : "Register"}
            </button>
          </form>

          {error && <p className="mt-4 text-sm text-rose-600">{error}</p>}

          <button
            className="mt-6 text-sm text-[#4f675f] hover:text-[#2f5f4c]"
            onClick={() => setMode(mode === "login" ? "register" : "login")}
          >
            {mode === "login" ? "Need an account? Register" : "Already have an account? Login"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-[#f4f1e8] via-[#eef3ec] to-[#e5ede6] text-[#203830] px-4 py-8 md:px-8">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-8">
          <div className="flex items-center gap-4">
            {showBrandLogo && (
              <img
                src={BRAND_LOGO_PATH}
                alt="ExpiryLens logo"
                className="h-14 w-14 rounded-2xl object-cover shadow-md shadow-[#7e8f7a]/20"
                onError={() => setShowBrandLogo(false)}
              />
            )}
            <div>
              <p className="text-xs tracking-[0.2em] uppercase text-[#7d6753]">{BRAND_TAGLINE}</p>
              <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-[#2f5f4c]">Welcome, {user.name}</h1>
              <p className="text-[#4f675f]">Manage products, track expiry dates, and get alerts with {BRAND_NAME}.</p>
            </div>
          </div>
          <button
            onClick={logout}
            className="rounded-xl border border-[#9db39d] px-4 py-2 text-[#2f5f4c] hover:bg-[#e2ece2]"
          >
            Logout
          </button>
        </div>

        {nearExpiryCount > 0 && (
          <div className="mb-6 rounded-2xl border border-[#bc9f7d]/50 bg-[#f0e3d2] p-4 text-[#7d6753]">
            Alert: {nearExpiryCount} product{nearExpiryCount > 1 ? "s are" : " is"} expiring in the next 7 days.
          </div>
        )}

        {error && (
          <div className="mb-6 rounded-2xl border border-rose-300 bg-rose-50 p-4 text-rose-700">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <section className="xl:col-span-1 rounded-3xl border border-[#c7d6c5] bg-[#f8f6f0]/90 p-6">
            <h2 className="text-xl font-semibold">Add Product (Manual)</h2>
            <form className="mt-4 space-y-3" onSubmit={addManualProduct}>
              <input
                className="w-full rounded-xl border border-[#c9d8c8] bg-[#ffffff] px-4 py-2.5 outline-none focus:border-[#2f7a62]"
                placeholder="Product name"
                value={newProductName}
                onChange={(e) => setNewProductName(e.target.value)}
                required
              />
              <input
                className="w-full rounded-xl border border-[#c9d8c8] bg-[#ffffff] px-4 py-2.5 outline-none focus:border-[#2f7a62]"
                placeholder="Expiry (e.g. 31/12/2026)"
                value={newExpiryDate}
                onChange={(e) => setNewExpiryDate(e.target.value)}
                required
              />
              <button className="w-full rounded-xl bg-[#2f7a62] text-[#f8f4eb] font-semibold py-2.5 hover:bg-[#25654f] transition" disabled={loading}>
                Add Product
              </button>
            </form>

            <h2 className="text-xl font-semibold mt-8">Add Product (Image OCR)</h2>
            <div className="mt-4 space-y-3">
              <input
                className="w-full rounded-xl border border-[#c9d8c8] bg-[#ffffff] px-4 py-2.5 outline-none focus:border-[#b97752]"
                placeholder="Product name"
                value={uploadProductName}
                onChange={(e) => setUploadProductName(e.target.value)}
                required
              />
              <input
                type="file"
                accept="image/*"
                onChange={onFileChange}
                className="w-full text-sm text-[#4f675f]"
              />
              {uploadPreview && (
                <img src={uploadPreview} alt="preview" className="h-40 w-full object-cover rounded-xl border border-[#c9d8c8]" />
              )}
              <button
                className="w-full rounded-xl bg-[#b97752] text-[#fff8ef] font-semibold py-2.5 hover:bg-[#a86a49] transition disabled:opacity-50"
                onClick={uploadProductImage}
                disabled={!uploadFile || !uploadProductName.trim() || loading}
              >
                Scan & Add Product
              </button>
            </div>
          </section>

          <section className="xl:col-span-2 rounded-3xl border border-[#c7d6c5] bg-[#f8f6f0]/90 p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold">Your Products</h2>
              <button className="rounded-xl border border-[#9db39d] px-3 py-1.5 text-sm text-[#2f5f4c] hover:bg-[#e2ece2]" onClick={refreshLists}>
                Refresh
              </button>
            </div>

            {products.length === 0 ? (
              <p className="text-[#4f675f]">No products yet. Add one manually or scan from image.</p>
            ) : (
              <div className="space-y-3">
                {products.map((product) => {
                  const isEditing = editingId === product.id;
                  return (
                    <div key={product.id} className="rounded-2xl border border-[#d2decf] bg-[#ffffff]/85 p-4">
                      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
                        {isEditing ? (
                          <div className="flex-1 grid grid-cols-1 md:grid-cols-2 gap-2">
                            <input
                              className="rounded-lg border border-[#c9d8c8] bg-[#ffffff] px-3 py-2"
                              value={editingName}
                              onChange={(e) => setEditingName(e.target.value)}
                            />
                            <input
                              className="rounded-lg border border-[#c9d8c8] bg-[#ffffff] px-3 py-2"
                              value={editingExpiry}
                              onChange={(e) => setEditingExpiry(e.target.value)}
                            />
                          </div>
                        ) : (
                          <div className="flex-1">
                            <p className="font-semibold text-lg">{product.productName}</p>
                            <p className="text-[#4f675f]">Expiry: {product.expiryDate}</p>
                            <p className="text-[#60766d] text-sm mt-1">
                              {product.daysRemaining < 0
                                ? `Expired ${Math.abs(product.daysRemaining)} day(s) ago`
                                : product.daysRemaining === 0
                                  ? "Expires today"
                                  : `${product.daysRemaining} day(s) left`}
                            </p>
                            {product.expiringSoon && (
                              <p className="text-[#a86a49] text-sm mt-1">Expiring soon ({product.daysRemaining} day(s) left)</p>
                            )}
                          </div>
                        )}

                        <div className="flex gap-2">
                          {isEditing ? (
                            <>
                              <button
                                className="rounded-lg bg-[#2f7a62] text-[#f8f4eb] px-3 py-2 text-sm font-medium"
                                onClick={() => saveEdit(product.id)}
                              >
                                Save
                              </button>
                              <button
                                className="rounded-lg border border-[#9db39d] px-3 py-2 text-sm"
                                onClick={() => setEditingId(null)}
                              >
                                Cancel
                              </button>
                            </>
                          ) : (
                            <>
                              <button
                                className="rounded-lg border border-[#9db39d] px-3 py-2 text-sm"
                                onClick={() => startEdit(product)}
                              >
                                Edit
                              </button>
                              <button
                                className="rounded-lg bg-rose-600 text-[#fff8ef] px-3 py-2 text-sm"
                                onClick={() => deleteProduct(product.id)}
                              >
                                Delete
                              </button>
                            </>
                          )}
                        </div>
                      </div>

                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

export default App;
