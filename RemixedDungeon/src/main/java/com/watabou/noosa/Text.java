package com.watabou.noosa;

import com.nyrds.android.util.ModdingMode;
import com.nyrds.pixeldungeon.ml.EventCollector;
import com.watabou.pixeldungeon.utils.Utils;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public abstract class Text extends Visual {

	@NotNull
	protected String text= Utils.EMPTY_STRING;


	protected static final Pattern PARAGRAPH	= Pattern.compile( "\n" );
	protected static final Pattern WORD			= Pattern.compile( "\\s+" );
	protected              int maxWidth = Integer.MAX_VALUE;
	
	protected boolean dirty = true;
	
	public boolean[] mask;
	
	protected Text(float x, float y, float width, float height) {
		super(x, y, width, height);
	}

	public static Text createBasicText(Font font) {
		if(!ModdingMode.getClassicTextRenderingMode()) {
			return new SystemText(font.baseLine * 2);
		}
		return new BitmapText(font);
	}
	
	public static Text createBasicText(String text,Font font) {
		if(!ModdingMode.getClassicTextRenderingMode()) {
			return new SystemText(text, font.baseLine * 2, false);
		}
		return new BitmapText(text, font);
	}

	public static Text create(Font font) {
		if(!ModdingMode.getClassicTextRenderingMode()) {
			return new SystemText(font.baseLine * 2);
		}
		return new BitmapText(font);
	}
	
	public static Text create(String text, Font font) {
		if(!ModdingMode.getClassicTextRenderingMode()) {
			return new SystemText(text, font.baseLine*2, false);
		}
		return new BitmapText(text, font);
	}
	
	public static Text createMultiline(String text, Font font) {
		
		if(!ModdingMode.getClassicTextRenderingMode()) {
			return new SystemText(text, font.baseLine * 2,true);
		}
		
		return new BitmapTextMultiline(text, font);
	}

	@Override
	public void draw(){
		clean();
		super.draw();
	}
	
	public int getMaxWidth() {
		return maxWidth;
	}

	protected void clean() {
		if(dirty) {
			measure();
			dirty = false;
		}
	}

	public void maxWidth(int maxWidth) {
		this.maxWidth = maxWidth;
		dirty = true;
	}

	@Override
	public float height() {
		clean();
		return super.height();
	}

	@Override
	public float width() {
		clean();
		return super.width();
	}

	protected abstract void measure();
	public abstract float baseLine();

	@NotNull
	public String text() {
		return text;
	}


	public void text(int id) {
		text(Game.getVar(id));
	}

	public void text(@NotNull String str) {
		dirty = true;

		if(str == null) {
		    text = Utils.EMPTY_STRING;
            EventCollector.logException("Trying to create null string!!!");
		    return;
        }

		text = str;
	}

	public abstract int lines();
}
