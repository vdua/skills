const isSlicc = typeof exec !== "undefined";
const defaultKeyFile = isSlicc
  ? "/optel/domainkey.json"
  : `${process.env.HOME}/.optel/domainkey.json`;
const DOMAINKEY_FILE = (process.env && process.env.DOMAINKEY_FILE) || defaultKeyFile;

console.log(`isSlicc: ${isSlicc}`);
console.log(`DOMAINKEY_FILE: ${DOMAINKEY_FILE}`);
