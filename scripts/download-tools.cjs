const fs=require('fs');
async function get(url,file){if(fs.existsSync(file))return;const r=await fetch(url);if(!r.ok)throw Error(url+': '+r.status);fs.writeFileSync(file,Buffer.from(await r.arrayBuffer()));console.log(file+' downloaded');}
Promise.all([get('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip','.toolchain/gradle.zip'),get('https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip','.toolchain/cmdtools.zip')]).catch(e=>{console.error(e);process.exitCode=1});
