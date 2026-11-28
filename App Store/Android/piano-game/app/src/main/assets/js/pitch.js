'use strict';

const down=new Float32Array(8192), diff=new Float32Array(4096);
function detectPitch(input,sampleRate){
  const stride=sampleRate>=40000?2:1, rate=sampleRate/stride, n=Math.floor(input.length/stride);
  if(n>down.length)return null;
  let mean=0;
  for(let i=0;i<n;i++){let s=0;for(let j=0;j<stride;j++)s+=input[i*stride+j];down[i]=s/stride;mean+=down[i];}
  mean/=n;let power=0;
  for(let i=0;i<n;i++){down[i]-=mean;power+=down[i]*down[i];}
  const rms=Math.sqrt(power/n);if(rms<0.004)return {rms,frequency:null};
  const minLag=Math.max(2,Math.floor(rate/2200)), maxLag=Math.min(diff.length-2,Math.ceil(rate/45),Math.floor(n/2)-2), window=n-maxLag;
  if(maxLag<=minLag+2)return {rms,frequency:null};
  let sum=0;diff[0]=1;
  for(let lag=1;lag<=maxLag+1;lag++){
    let d=0;for(let i=0;i<window;i++){const delta=down[i]-down[i+lag];d+=delta*delta;}
    sum+=d;diff[lag]=sum?d*lag/sum:1;
  }
  let best=-1;
  for(let lag=minLag;lag<=maxLag;lag++){
    if(diff[lag]<0.16){while(lag<maxLag&&diff[lag+1]<diff[lag])lag++;best=lag;break;}
  }
  if(best<0){let v=1;for(let lag=minLag;lag<=maxLag;lag++){if(diff[lag]<v){v=diff[lag];best=lag;}}if(v>0.25)return {rms,frequency:null};}
  const a=diff[best-1],b=diff[best],c=diff[best+1],den=a-2*b+c;
  const refined=best+(Math.abs(den)>1e-9?Math.max(-0.5,Math.min(0.5,0.5*(a-c)/den)):0);
  return {rms,frequency:rate/refined,confidence:1-b};
}
