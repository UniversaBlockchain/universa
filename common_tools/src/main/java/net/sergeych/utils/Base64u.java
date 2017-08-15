/*
 * [Base64u.java]
 *
 * Summary: Works exactly like Base64 but safe in URL contexts.
 *
 * Copyright: (c) 1999-2014 Roedy Green, Canadian Mind Products, http://mindprod.com
 *
 * Licence: This software may be copied and used freely for any purpose but military.
 *          http://mindprod.com/contact/nonmil.html
 *
 * Requires: JDK 1.7+
 *
 * Created with: JetBrains IntelliJ IDEA IDE http://www.jetbrains.com/idea/
 *
 * Version History:
 *  1.9 2007-05-20 add icon and pad
 */
package net.sergeych.utils;

/**
 * Works exactly like Base64 but safe in URL contexts.
 * <p>
 * It avoids using the characters
 * + / and =.  This means Base64u-encoded data can be used either
 * URLCoded or plain in
 * URL-Encoded contexts such as GET, PUT or URLs. You can treat the
 * output either as
 * not needing encoding or already URLEncoded.
 * <p>
 *
 * @author Roedy Green, Canadian Mind Products
 * @version 1.9 2007-05-20 add icon and pad
 * @since 1999-12-03
 */
public final class Base64u extends Base64
    {
    /**
     * binary value encoded by a given letter of the alphabet 0..63.
     */
    private static int[] cv;

    /**
     * letter of the alphabet used to encode binary values 0..63.
     */
    private static char[] vc;

    /**
     * constructor.
     */
    public Base64u()
        {
        spec1 = '-';
        spec2 = '_';
        spec3 = '*';
        initTables();
        }

    /**
     * test driver.
     *
     * @param args not used .
     *
     */
    public static void main( String[] args )
        {
        if ( DEBUGGING )
            {
            byte[] a = { ( byte ) 0xfc, ( byte ) 0x0f, ( byte ) 0xc0 };
            byte[] b = { ( byte ) 0x03, ( byte ) 0xf0, ( byte ) 0x3f };
            byte[] c = { ( byte ) 0x00, ( byte ) 0x00, ( byte ) 0x00 };
            byte[] d = { ( byte ) 0xff, ( byte ) 0xff, ( byte ) 0xff };
            byte[] e = { ( byte ) 0xfc, ( byte ) 0x0f, ( byte ) 0xc0, ( byte ) 1 };
            byte[] f =
                    { ( byte ) 0xfc, ( byte ) 0x0f, ( byte ) 0xc0, ( byte ) 1, ( byte ) 2 };
            byte[] g = {
                    ( byte ) 0xfc,
                    ( byte ) 0x0f,
                    ( byte ) 0xc0,
                    ( byte ) 1,
                    ( byte ) 2,
                    ( byte ) 3 };
            byte[] h = "AAAAAAAAAAB".getBytes();
            show( a );
            show( b );
            show( c );
            show( d );
            show( e );
            show( f );
            show( g );
            show( h );
            Base64u b64 = new Base64u();
            show( b64.decode( b64.encode( a ) ) );
            show( b64.decode( b64.encode( b ) ) );
            show( b64.decode( b64.encode( c ) ) );
            show( b64.decode( b64.encode( d ) ) );
            show( b64.decode( b64.encode( e ) ) );
            show( b64.decode( b64.encode( f ) ) );
            show( b64.decode( b64.encode( g ) ) );
            show( b64.decode( b64.encode( h ) ) );
            b64.setLineLength( 8 );
            show( ( b64.encode( h ) ).getBytes() );
            }
        } // end gui.main.main

    /**
     * Initialise both static and instance table.
     */
    private void initTables()
        {
        if ( vc == null )
            {
            // statics are not initialised yet
            vc = new char[ 64 ];
            cv = new int[ 256 ];
            // build translate valueToChar table only once.
            // 0..25 -> 'A'..'Z'
            for ( int i = 0; i <= 25; i++ )
                {
                vc[ i ] = ( char ) ( 'A' + i );
                }
            // 26..51 -> 'a'..'z'
            for ( int i = 0; i <= 25; i++ )
                {
                vc[ i + 26 ] = ( char ) ( 'a' + i );
                }
            // 52..61 -> '0'..'9'
            for ( int i = 0; i <= 9; i++ )
                {
                vc[ i + 52 ] = ( char ) ( '0' + i );
                }
            vc[ 62 ] = spec1;
            vc[ 63 ] = spec2;
            // build translate charToValue table only once.
            for ( int i = 0; i < 256; i++ )
                {
                cv[ i ] = IGNORE;// default is to ignore
                }
            for ( int i = 0; i < 64; i++ )
                {
                cv[ vc[ i ] ] = i;
                }
            cv[ spec3 ] = PAD;
            }
        valueToChar = vc;
        charToValue = cv;
        }
    } // end Base64
