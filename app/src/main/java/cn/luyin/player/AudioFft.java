package cn.luyin.player;

/** In-place radix-2 FFT. Only the analysis worker owns these arrays. */
public final class AudioFft {
    public static void forward(double[] real,double[] imag){
        int n=real.length;if(n<2||(n&(n-1))!=0||imag.length!=n)throw new IllegalArgumentException("FFT requires equal power-of-two arrays");
        for(int i=1,j=0;i<n;i++){int bit=n>>1;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double t=real[i];real[i]=real[j];real[j]=t;t=imag[i];imag[i]=imag[j];imag[j]=t;}}
        for(int len=2;len<=n;len<<=1){double angle=-2*Math.PI/len,wr0=Math.cos(angle),wi0=Math.sin(angle);for(int start=0;start<n;start+=len){double wr=1,wi=0;for(int j=0;j<len/2;j++){
            int a=start+j,b=a+len/2;double tr=real[b]*wr-imag[b]*wi,ti=real[b]*wi+imag[b]*wr;real[b]=real[a]-tr;imag[b]=imag[a]-ti;real[a]+=tr;imag[a]+=ti;double next=wr*wr0-wi*wi0;wi=wr*wi0+wi*wr0;wr=next;
        }}}
    }
    private AudioFft(){}
}
