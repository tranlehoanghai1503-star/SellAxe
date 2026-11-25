SellAxe by Hải Trần - addon Meteor cho Minecraft 1.21.11 (Fabric)

Gồm 2 module: SellAxe và AutoPay.

BUILD TRÊN GITHUB
1) Đưa TOÀN BỘ nội dung thư mục này (không phải cả thư mục cha) lên repo GitHub,
   nhớ có thư mục ẩn .github/workflows/build.yml.
2) Vào tab Actions, chọn "Build SellAxe" (hoặc đẩy một commit mới để nó tự chạy).
3) Khi chạy xong (dấu tick xanh), mở lần chạy đó, kéo xuống mục Artifacts, tải "SellAxe".
   Giải nén ra file SellAxe-1.5.0.jar (dùng file không có -sources).
4) Bỏ vào thư mục mods cùng Fabric Loader, Fabric API và Meteor Client cho 1.21.11.
   Xoá hết các file SellAxe cũ trước khi bỏ bản mới.

BUILD TRÊN MÁY (cần JDK 21 và Gradle 9.2 trở lên): gradle build
File jar nằm trong build/libs/.
