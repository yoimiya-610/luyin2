package cn.luyin.player;

/** AAC-LC ADTS transport header; a .aac export contains actual AAC access units. */
public final class AdtsHeader {
    public static byte[] forFrame(int payloadBytes,int sampleRate,int channels){
        int[] rates={96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350};int index=-1;for(int i=0;i<rates.length;i++)if(rates[i]==sampleRate)index=i;
        int length=payloadBytes+7;if(index<0||channels<1||channels>7||payloadBytes<1||length>8191)throw new IllegalArgumentException("Invalid ADTS frame");
        return new byte[]{(byte)0xff,(byte)0xf1,(byte)((1<<6)|(index<<2)|(channels>>2)),(byte)(((channels&3)<<6)|(length>>11)),(byte)(length>>3),(byte)(((length&7)<<5)|0x1f),(byte)0xfc};
    }
    private AdtsHeader(){}
}
