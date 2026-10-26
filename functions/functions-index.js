const {onDocumentCreated, onDocumentUpdated} = require("firebase-functions/v2/firestore");
const {setGlobalOptions} = require("firebase-functions/v2");
const admin = require("firebase-admin");
admin.initializeApp();

// Must match where your Firestore database lives (see the guide, step 6).
const REGION = "us-central1";
setGlobalOptions({region: REGION, maxInstances: 5});

const money = (n) => Number(n || 0).toLocaleString("en-US") + " AMD";

// 1) New order -> push to every signed-in admin phone
exports.notifyAdminNewOrder = onDocumentCreated("orders/{id}", async (event) => {
  const o = event.data.data();
  const snap = await admin.firestore().collection("adminTokens").get();
  const tokens = snap.docs.map((d) => d.id);
  if (!tokens.length) return;
  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {title: `New order ${o.no}`, body: `${o.mode} · ${o.name} · ${money(o.total)}`},
    android: {priority: "high", ttl: 60000, notification: {channelId: "orders_alarm", sound: "order_alert", visibility: "public", notificationPriority: "PRIORITY_MAX", defaultVibrateTimings: false, vibrateTimingsMillis: [0, 800, 300, 800, 300, 800]}},
    data: {orderId: event.params.id},
  });
  const dead = [];
  res.responses.forEach((r, i) => {
    const c = r.error && r.error.code;
    if (!r.success && (c === "messaging/registration-token-not-registered" || c === "messaging/invalid-registration-token")) dead.push(tokens[i]);
  });
  await Promise.all(dead.map((t) => admin.firestore().doc("adminTokens/" + t).delete()));
});

// 2) Status / ETA change -> push to the customer who placed the order
const L = {
  en: {Accepted: "Order confirmed", Preparing: "Being prepared", Ready_Delivery: "Packed — courier soon", Ready_Pickup: "Ready for pickup", Ready_Dine: "Your table is ready", OnTheWay: "On the way", Done: "Completed — enjoy!", Declined: "Sorry, we couldn't accept this order", Cancelled: "Your order was cancelled", Edited: "Your order was updated", min: "min"},
  hy: {Accepted: "Պատվերը հաստատվեց", Preparing: "Պատրաստվում է", Ready_Delivery: "Փաթեթավորված է՝ շուտով կառաքվի", Ready_Pickup: "Պատրաստ է վերցնելու համար", Ready_Dine: "Ձեր սեղանը պատրաստ է", OnTheWay: "Ճանապարհին է", Done: "Ավարտված է՝ բարի ախորժակ", Declined: "Ցավոք, չենք կարող ընդունել այս պատվերը", Cancelled: "Ձեր պատվերը չեղարկվել է", Edited: "Ձեր պատվերը թարմացվել է", min: "ր"},
  ru: {Accepted: "Заказ подтверждён", Preparing: "Готовится", Ready_Delivery: "Упакован — скоро курьер", Ready_Pickup: "Готов к выдаче", Ready_Dine: "Ваш столик готов", OnTheWay: "В пути", Done: "Завершён — приятного аппетита!", Declined: "К сожалению, мы не смогли принять заказ", Cancelled: "Ваш заказ отменён", Edited: "Ваш заказ обновлён", min: "мин"},
  ar: {Accepted: "تم تأكيد الطلب", Preparing: "قيد التحضير", Ready_Delivery: "جاهز — السائق قريباً", Ready_Pickup: "جاهز للاستلام", Ready_Dine: "طاولتك جاهزة", OnTheWay: "في الطريق", Done: "اكتمل — بالهناء والشفاء!", Declined: "عذراً، لم نتمكن من قبول الطلب", Cancelled: "تم إلغاء طلبك", Edited: "تم تعديل طلبك", min: "د"},
};
exports.notifyCustomerStatus = onDocumentUpdated("orders/{id}", async (event) => {
  const a = event.data.after.data();
  const b = event.data.before.data();
  if (!a.fcm || a.status === "New") return;
  const T = L[a.lang] || L.en;
  let body = null;
  if (a.lastMsgTs && a.lastMsgTs !== b.lastMsgTs) {
    // 3) Free-text message from the shop
    body = a.lastMsg;
  } else if (a.status !== b.status || a.etaMin !== b.etaMin) {
    const key = a.status === "Ready" ? "Ready_" + a.mode : a.status;
    body = T[key];
    if (!body) return;
    if (a.status === "Cancelled" && a.reason) body += ` · ${a.reason}`;
    if (a.etaMin && ["Accepted", "Preparing", "Ready", "OnTheWay"].includes(a.status) && a.mode !== "Dine") body += ` · ~${a.etaMin} ${T.min}`;
    if (a.confirmTime && a.mode === "Dine") body += ` · ${a.confirmTime}`;
  } else if (a.editTs && a.editTs !== b.editTs) {
    // 4) Shop edited the order
    body = `${T.Edited} · ${money(a.total)}`;
  }
  if (!body) return;
  try {
    await admin.messaging().send({
      token: a.fcm,
      notification: {title: `Skhtor · ${a.no}`, body},
      android: {priority: "high", notification: {channelId: "orders", sound: "default"}},
    });
  } catch (e) { /* stale token: ignore */ }
});
