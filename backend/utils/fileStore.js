import fs from "fs";
import path from "path";

function readJson(relativePath) {
  const filePath = path.resolve(relativePath);

  if (!fs.existsSync(filePath)) {
    return [];
  }

  const raw = fs.readFileSync(filePath, "utf-8");
  return raw ? JSON.parse(raw) : [];
}

function writeJson(relativePath, data) {
  const filePath = path.resolve(relativePath);
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
}

function appendJson(relativePath, record) {
  const data = readJson(relativePath);
  data.push(record);
  writeJson(relativePath, data);
}

export {
  readJson,
  writeJson,
  appendJson
};
