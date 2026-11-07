// Generado con pos/herramientas/datos_kotlin.py desde lo que escribe pos/herramientas/oraculo. No se edita a mano.
package mx.papeleriapos.pos.busqueda

/**
 * Lo que dio la búsqueda del POS (sus fuentes de C# tal cual) con el catálogo de su base de prueba: GeneradorFicticios con 30
 * días, como ServicioDePrueba, con los Id cambiados por unos que se leen como su lugar (en el mismo orden). [BaseDePrueba] lo lee.
 */
internal object BaseDePruebaDatos {
    /** Los productos en el orden de su Id: descripción|código|códigos|vendidos|huella de sus partes (texto/eje/clave). */
    val PRODUCTOS: List<String> = listOf(PRODUCTOS_1, PRODUCTOS_2, PRODUCTOS_3, PRODUCTOS_4)

    private const val PRODUCTOS_1 = """
Pluma punto fino Bic Azul|7508500638943|7508500638943 7508669136854|11|b28d43b9
Pluma punto fino Bic Negro|7503505687545|7503505687545|13|4dfe901d
Pluma punto fino Bic Rojo|7509236441487|7509236441487 7507881483364|8|cd12e079
Pluma punto fino Bic Verde|7502096924824|7502096924824|19|04ecaccb
Pluma punto fino Pelikan Azul|7501672608547|7501672608547 7502164286144|13|5725600d
Pluma punto fino Pelikan Negro|7504667118632|7504667118632|3|2d00e771
Pluma punto fino Pelikan Rojo|7509533121341|7509533121341 7507870557458|4|71aafccd
Pluma punto fino Pelikan Verde|7505226510928|7505226510928 7509854901929|3|e3ef041f
Pluma punto fino Paper Mate Azul|7509617977048|7509617977048|6|38a9119f
Pluma punto fino Paper Mate Negro|||6|bc5e7c83
Pluma punto fino Paper Mate Rojo|7508484830142|7508484830142 7505810944740|7|532eae5f
Pluma punto fino Paper Mate Verde|7500800372411|7500800372411|12|734c9931
Pluma punto fino Zebra Azul|7509841710756|7509841710756 7501057983511|1|119a224d
Pluma punto fino Zebra Negro|||1|1d4e19b1
Pluma punto fino Zebra Rojo|7508687664032|7508687664032|4|2c1fbf0d
Pluma punto fino Zebra Verde|7504489932928|7504489932928|1|d43c365f
Pluma punto fino Pilot Azul|7504071089818|7504071089818|6|0008f6b1
Pluma punto fino Pilot Negro|7502750031370|7502750031370 7506253633994|4|2b596515
Pluma punto fino Pilot Rojo|7503867465041|7503867465041|6|1a8e9371
Pluma punto fino Pilot Verde|7504196992864|7504196992864|1|e24781c3
Pluma punto mediano Bic Azul|7502619068332|7502619068332|6|b557356b
Pluma punto mediano Bic Negro|7504056225019|7504056225019|4|c612dd4f
Pluma punto mediano Bic Rojo|7505440350218|7505440350218|14|cfdcd22b
Pluma punto mediano Kores Azul|7509313608888|7509313608888|0|cfff7d6b
Pluma punto mediano Kores Negro|7506708721269|7506708721269 7502500246375|8|d7c9254f
Pluma punto mediano Kores Rojo|7507530733970|7507530733970 7501478981233|7|ea851a2b
Pluma punto mediano Paper Mate Azul|7501394277229|7501394277229 7507650497400|6|6b2b832d
Pluma punto mediano Paper Mate Negro|7501393316769|7501393316769 7506663739552|1|580ac291
Pluma punto mediano Paper Mate Rojo|7503380828828|7503380828828|15|85b11fed
Pluma de gel Pilot Blanco|7500940732830|7500940732830|5|5d6c0b68
Pluma de gel Pilot Amarillo|7508425977523|7508425977523|0|f67c86e8
Pluma de gel Pilot Rosa|7500907731388|7500907731388|0|827112e8
Pluma de gel Pilot Azul|7506746391110|7506746391110|0|710745c8
Pluma de gel Pilot Verde|7506267386985|7506267386985|3|0cee671a
Pluma de gel Pilot Rojo|7505101244597|7505101244597|0|8b8ce288
Pluma de gel Pilot Naranja|7502355303063|7502355303063|1|f8ec64d8
Pluma de gel Pilot Negro|7504840673231|7504840673231|0|56004a6c
Pluma de gel Pilot Morado|7507585537554|7507585537554|9|d525e808
Pluma de gel Pilot Café|7502266006824|7502266006824|2|de2650a4
Pluma de gel Zebra Blanco|7504565739809|7504565739809|7|a5060904
Pluma de gel Zebra Amarillo|7504488620017|7504488620017|1|bf665684
Pluma de gel Zebra Rosa|7509545709360|7509545709360|0|94023e84
Pluma de gel Zebra Azul|7505088357945|7505088357945|0|82987164
Pluma de gel Zebra Verde|7505876015583|7505876015583|0|fee31bb6
Pluma de gel Zebra Rojo|7505991289814|7505991289814|0|9d1e0e24
Pluma de gel Zebra Naranja|7502844455969|7502844455969|2|c1fd6b74
Pluma de gel Zebra Negro|||1|47f4ff08
Pluma de gel Zebra Morado|7508841635069|7508841635069|1|1cbfe5a4
Pluma de gel Zebra Café|7509334161447|7509334161447|0|efb77c40
Pluma de gel Pelikan Blanco|7500808092052|7500808092052|1|7a1ec344
Pluma de gel Pelikan Amarillo|7505170830776|7505170830776|0|8917f0c4
Pluma de gel Pelikan Rosa|7508963549237|7508963549237|5|851218c4
Pluma de gel Pelikan Azul|7504721110510|7504721110510|2|73a84ba4
Pluma de gel Pelikan Verde|7500752886110|7500752886110|0|eb6565f6
Pluma de gel Pelikan Rojo|7500210402432|7500210402432|3|8e2de864
Pluma de gel Pelikan Naranja|7501697169276|7501697169276|2|b3d095b4
Pluma de gel Pelikan Negro|7505478159463|7505478159463|0|34774948
Pluma de gel Pelikan Morado|||0|f1d89fe4
Pluma de gel Pelikan Café|7500884456229|7500884456229|2|e0c75680
Pluma de gel Paper Mate Blanco|7507882264634|7507882264634|0|b962e908
Pluma de gel Paper Mate Amarillo|7509114811487|7509114811487|1|9a8b9488
Pluma de gel Paper Mate Rosa|7505360632951|7505360632951|0|e2b7c088
Pluma de gel Paper Mate Azul|7503855107311|7503855107311|1|d14df368
Pluma de gel Paper Mate Verde|7506812042618|7506812042618|6|76402cba
Pluma de gel Paper Mate Rojo|7502594293606|7502594293606|1|ebd39028
Pluma de gel Paper Mate Naranja|7500827917329|7500827917329|1|32a25a78
Pluma de gel Paper Mate Negro|7504902246762|7504902246762|0|bf52100c
Pluma de gel Paper Mate Morado|7509626006876|7509626006876|4|311cc5a8
Pluma de gel Paper Mate Café|7509793321055|7509793321055|1|3e6cfe44
Lápiz del 2 Mirado Hexagonal|7504552968199|7504552968199|3|e412a0c9
Lápiz del 2 Mirado Triangular|7506290075955|7506290075955|7|6f4a5c77
Lápiz del 2 Dixon Hexagonal|7503582470870|7503582470870|5|391141d5
Lápiz del 2 Dixon Triangular|||15|7f24ea83
Lápiz del 2 Pelikan Hexagonal|7509945529346|7509945529346 7504874540899|2|b02cc455
Lápiz del 2 Pelikan Triangular|7502651352598|7502651352598 7503411517325|30|9d69cd03
Lápiz del 2 Maped Hexagonal|7506928747773|7506928747773|0|00ecc1f5
Lápiz del 2 Maped Triangular|7501612115104|7501612115104 7507006646711|2|be20e2a3
Lápiz del 2 Staedtler Hexagonal|7508749235781|7508749235781|18|b7a3d715
Lápiz del 2 Staedtler Triangular|7504680434726|7504680434726|15|a3672fc3
Portaminas Pentel 0.5 mm|7505493074550|7505493074550|0|e5af9736
Portaminas Pentel 0.7 mm|7501839405729|7501839405729|5|b1cbc476
Portaminas Pilot 0.5 mm|||0|fc2a0302
Portaminas Pilot 0.7 mm|||0|c8463042
Portaminas Bic 0.5 mm|7505307850073|7505307850073|1|62acd40a
Portaminas Bic 0.7 mm|7505158128604|7505158128604|1|2ec9014a
Portaminas Pelikan 0.5 mm|7505139540982|7505139540982|0|ee7d725e
Portaminas Pelikan 0.7 mm|7508821476262|7508821476262|1|ba999f9e
Minas Pentel 0.5 mm HB|7501590063541|7501590063541|1|07a68584
Minas Pentel 0.7 mm HB|7501006777956|7501006777956|2|3138f044
Minas Pentel 0.5 mm 2B|7505618493853|7505618493853|0|e21be530
Minas Pilot 0.5 mm HB|7502779955978|7502779955978|1|df76d9a8
Minas Pilot 0.7 mm HB|||1|09094468
Minas Pilot 0.5 mm 2B|7509164469300|7509164469300|1|b9ec3954
Marcatextos Sharpie Amarillo|7505804482005|7505804482005|4|0aec4345
Marcatextos Sharpie Verde|7506938999056|7506938999056 7509814204473|5|ad85ed37
Marcatextos Sharpie Rosa|7509820504260|7509820504260|1|747d7c45
Marcatextos Sharpie Naranja|||0|d7e99475
Marcatextos Sharpie Azul|7503999968991|7503999968991|1|6313af25
Marcatextos Sharpie Lila|7503858409023|7503858409023|7|888e45e5
Marcatextos Sharpie Pastel verde|7501493432444|7501493432444|6|32807a7f
Marcatextos Sharpie Pastel rosa|7501983873818|7501983873818|4|97cabb8d
Marcatextos Pelikan Amarillo|7507013384781|7507013384781|1|a925f305
Marcatextos Pelikan Verde|7507373015851|7507373015851|2|25ae6cf7
Marcatextos Pelikan Rosa|7504473116600|7504473116600 7503424842803|0|c842ec05
Marcatextos Pelikan Naranja|7509158544808|7509158544808 7508668123862|6|044c3435
Marcatextos Pelikan Azul|7508169000075|7508169000075|3|b6d91ee5
Marcatextos Pelikan Lila|7506806130659|7506806130659|6|dc53b5a5
Marcatextos Pelikan Pastel verde|7506492918494|7506492918494|8|bc3c6b85
Marcatextos Pelikan Pastel rosa|7503296845810|7503296845810 7505745298499|5|5bbb3613
Marcatextos Bic Amarillo|7505000848438|7505000848438|5|78d5c2b1
Marcatextos Bic Verde|7503271038947|7503271038947 7506491440897|5|2aae6da3
Marcatextos Bic Rosa|7507197943132|7507197943132|1|546327b1
Marcatextos Bic Naranja|7503434410511|7503434410511|8|92c3fee1
Marcatextos Bic Azul|7501841746346|7501841746346 7504904192968|7|42f95a91
Marcatextos Bic Lila|7509019768978|7509019768978|1|6873f151
Marcatextos Bic Pastel verde|7507209904373|7507209904373 7501882534575|1|708e03bf
Marcatextos Bic Pastel rosa|7503835283455|7503835283455|0|f22994cd
Marcatextos Zebra Amarillo|7503848454446|7503848454446|1|8b89a145
Marcatextos Zebra Verde|7507352638101|7507352638101|6|cf01cb37
Marcatextos Zebra Rosa|7507589704273|7507589704273 7502799976762|1|df58da45
Marcatextos Zebra Naranja|7501222822669|7501222822669 7509767889970|2|fc467275
Marcatextos Zebra Azul|||0|cdef0d25
Marcatextos Zebra Lila|7507425552365|7507425552365|2|f369a3e5
Marcatextos Zebra Pastel verde|7502964269934|7502964269934 7505924983390|0|571b8fff
Marcatextos Zebra Pastel rosa|||3|0b2d310d
Marcador permanente Sharpie Azul|7500292107829|7500292107829|4|ffdc503d
Marcador permanente Sharpie Negro|7500284661810|7500284661810|7|83bc8ba1
Marcador permanente Sharpie Rojo|7501794320846|7501794320846|1|1a61ecfd
Marcador permanente Sharpie Verde|7505616193236|7505616193236|2|3aaaa84f
Marcador permanente Pelikan Azul|||2|53a1bffd
Marcador permanente Pelikan Negro|7507532940352|7507532940352|2|fbe50b61
Marcador permanente Pelikan Rojo|7502124168732|7502124168732|2|6e275cbd
Marcador permanente Pelikan Verde|7506349958710|7506349958710|1|b2d3280f
Marcador permanente Bic Azul|7502851336817|7502851336817|0|53a093a9
Marcador permanente Bic Negro|7501575060701|7501575060701|0|f77da40d
Marcador permanente Bic Rojo|7504940142057|7504940142057|1|6e263069
Marcador permanente Bic Verde|7508680144692|7508680144692|0|ae6bc0bb
Marcador permanente Azor Azul|7507048490327|7507048490327|0|97c97495
Marcador permanente Azor Negro|7502133723113|7502133723113|0|d4f1f9f9
Marcador permanente Azor Rojo|7504812448867|7504812448867|0|b24f1155
Marcador permanente Azor Verde|7506845421145|7506845421145|1|8be016a7
Marcador para pizarrón Expo Azul|7509370563502|7509370563502|4|8af2a0ad
Marcador para pizarrón Expo Negro|7509417134702|7509417134702|1|a2808011
Marcador para pizarrón Expo Rojo|7505448319767|7505448319767|3|a5783d6d
Marcador para pizarrón Expo Verde|7502445790643|7502445790643|4|596e9cbf
Marcador para pizarrón Pelikan Azul|7502664622503|7502664622503|3|d9c3b3d5
Marcador para pizarrón Pelikan Negro|7500986634365|7500986634365|0|81596939
Marcador para pizarrón Pelikan Rojo|7509366319632|7509366319632|1|f4495095
Marcador para pizarrón Pelikan Verde|7507253418529|7507253418529|3|384785e7
Marcador para pizarrón Azor Azul|7507411524246|7507411524246|3|0c052a6d
Marcador para pizarrón Azor Negro|7500707798437|7500707798437|0|291799d1
Marcador para pizarrón Azor Rojo|7509393437002|7509393437002|1|268ac72d
Marcador para pizarrón Azor Verde|7502593843253|7502593843253|0|e005b67f
Marcador para pizarrón Baco Azul|7501403010175|7501403010175|0|f7097b0d
Marcador para pizarrón Baco Negro|7504197502239|7504197502239|2|644a4271
Marcador para pizarrón Baco Rojo|7507806569647|7507806569647|4|118f17cd
Marcador para pizarrón Baco Verde|7508244957263|7508244957263|0|1b385f1f
Corrector líquido Pelikan Pluma|||3|f9ddd6a4
Corrector líquido Pelikan Frasco|7505787149506|7505787149506|2|bf3ac5f8
Corrector líquido Bic Pluma|7508087660108|7508087660108|0|b9795fe4
Corrector líquido Bic Frasco|7506463753246|7506463753246|7|0618ff38
Corrector líquido Kores Pluma|7500702745535|7500702745535|4|799a0ae4
Corrector líquido Kores Frasco|7505167743645|7505167743645|4|40baea38
Corrector en cinta Bic|7504470002562|7504470002562|0|1bcf274d
Corrector en cinta Pelikan|7509384699464|7509384699464|0|f0c6eca1
Corrector en cinta Maped|7501450681953|7501450681953|2|13b526cb
Goma de borrar Pelikan Blanca|7506618704086|7506618704086|8|4ea1da66
Goma de borrar Pelikan Migajón|7504621725869|7504621725869|13|70e9d777
Goma de borrar Factis Blanca|||15|4da6227e
Goma de borrar Factis Migajón|7501035691483|7501035691483|2|d642116b
Goma de borrar Maped Blanca|7504614749346|7504614749346|2|81c7e990
Goma de borrar Maped Migajón|7504335619720|7504335619720 7501183003091|0|50c7c417
Goma de borrar Staedtler Blanca|7506813143499|7506813143499|2|2567d5e2
Goma de borrar Staedtler Migajón|7502249504590|7502249504590|0|2f41ab37
Goma de borrar Pentel Blanca|7503421364810|7503421364810|1|c2f8edfe
Goma de borrar Pentel Migajón|7506400080268|7506400080268 7503819837971|4|723898d3
Sacapuntas Maped Metálico|7507606834440|7507606834440|11|bf2c8c6e
Sacapuntas Maped Con depósito|7501872276508|7501872276508|2|3ffb78f6
Sacapuntas Maped Doble|7504683194184|7504683194184|11|137ed0b2
Sacapuntas Pelikan Metálico|7506094041002|7506094041002|2|4aa5d04e
Sacapuntas Pelikan Con depósito|||4|17aa54d6
Sacapuntas Pelikan Doble|7501546427663|7501546427663|2|f4b07c92
Sacapuntas Barrilito Metálico|7505269438357|7505269438357|3|6d5e0b4e
Sacapuntas Barrilito Con depósito|7502407132566|7502407132566|1|600a4fd6
Sacapuntas Barrilito Doble|7505922990970|7505922990970|2|a8f0f792
Sacapuntas Staedtler Metálico|7503554214419|7503554214419|3|ca6dfc8e
Sacapuntas Staedtler Con depósito|7502527266424|7502527266424|13|6d305116
Sacapuntas Staedtler Doble|7502412752469|7502412752469|2|481ed8d2
Plumones de colores Crayola Caja 10|7505111101736|7505111101736|6|595b622f
Plumones de colores Crayola Caja 12|7504772640141|7504772640141|0|59779133
Plumones de colores Crayola Caja 24|7505286834736|7505286834736|0|5b4898f5
Plumones de colores Pelikan Caja 10|7509569895087|7509569895087|4|0a7a644f
Plumones de colores Pelikan Caja 12|7501486085251|7501486085251|2|0a969353
Plumones de colores Pelikan Caja 24|7503495614361|7503495614361|0|0c679b15
Plumones de colores Maped Caja 10|||0|65cee2af
Plumones de colores Maped Caja 12|7501514786921|7501514786921|1|65eb11b3
Plumones de colores Maped Caja 24|7500003264711|7500003264711|2|67bc1975
Plumones de colores Baco Caja 10|7505308479693|7505308479693|1|bd69b1c5
Plumones de colores Baco Caja 12|7502780594401|7502780594401|1|bd85e0c9
Plumones de colores Baco Caja 24|7507902225447|7507902225447|2|bf56e88b
Colores Crayola Caja 12|7508816396360|7508816396360|7|dba80598
Colores Crayola Caja 24|7509109115958|7509109115958|4|dd790d5a
Colores Crayola Caja 36|7508107844235|7508107844235|3|df4a151c
Colores Prismacolor Caja 12|||6|b72ab5d8
Colores Prismacolor Caja 24|7507378487790|7507378487790|1|b8fbbd9a
Colores Prismacolor Caja 36|7504374044392|7504374044392|1|baccc55c
Colores Mapita Caja 12|7507370564888|7507370564888|9|8323d14c
Colores Mapita Caja 24|7501187673825|7501187673825|10|84f4d90e
Colores Mapita Caja 36|7503094750613|7503094750613|3|86c5e0d0
Colores Maped Caja 12|7500903985860|7500903985860|0|eb678098
Colores Maped Caja 24|7509116288034|7509116288034|1|ed38885a
Colores Maped Caja 36|7509876720744|7509876720744|2|ef09901c
Colores Pelikan Caja 12|||2|8cc707b8
Colores Pelikan Caja 24|7507378516803|7507378516803|2|8e980f7a
Colores Pelikan Caja 36|7504727374152|7504727374152|0|9069173c
Colores Norma Caja 12|7509608008775|7509608008775|2|67d48d58
Colores Norma Caja 24|7500441849907|7500441849907|5|69a5951a
Colores Norma Caja 36|7503581103786|7503581103786|0|6b769cdc
Crayones Crayola Caja 12|||0|055eb470
Crayones Crayola Caja 24|7504072094323|7504072094323|3|072fbc32
Crayones Baco Caja 12|7508945294445|7508945294445|5|216375c6
Crayones Baco Caja 24|7507854892810|7507854892810|0|23347d88
Crayones Pelikan Caja 12|7509252150127|7509252150127|6|b67db690
Crayones Pelikan Caja 24|7509709920129|7509709920129|0|b84ebe52
Bolígrafo multicolor Bic 4 colores|7500016362206|7500016362206|2|f432ae01
Lápiz de color suelto Prismacolor Blanco|7508748190524|7508748190524|0|8db39228
Lápiz de color suelto Prismacolor Negro|7503383164503|7503383164503|5|1bfa812c
Lápiz de color suelto Prismacolor Gris frío|7503780798486|7503780798486|6|7f6ba7ee
Lápiz de color suelto Prismacolor Amarillo canario|7507635079164|7507635079164|0|1e1f8965
Lápiz de color suelto Prismacolor Amarillo limón|7502509960487|7502509960487|3|2be9bd19
Lápiz de color suelto Prismacolor Naranja|7502395264089|7502395264089|0|356d3b98
Lápiz de color suelto Prismacolor Rojo carmín|7504870318232|7504870318232|3|83f7bd67
Lápiz de color suelto Prismacolor Rojo escarlata|7500080597023|7500080597023|2|6b2b684e
Lápiz de color suelto Prismacolor Rosa|7507437430064|7507437430064|5|0b09f9a8
Lápiz de color suelto Prismacolor Magenta|7509557566227|7509557566227|5|fefcdae4
Lápiz de color suelto Prismacolor Violeta|||4|8ef64156
Lápiz de color suelto Prismacolor Lila|7501263913708|7501263913708|1|1f1ac348
Lápiz de color suelto Prismacolor Azul ultramar|7501838459587|7501838459587|2|5ca7b273
Lápiz de color suelto Prismacolor Azul cielo|7507590195800|7507590195800|4|9947050f
Lápiz de color suelto Prismacolor Azul verdoso|||7|6b8b904e
Lápiz de color suelto Prismacolor Turquesa|7504420044918|7504420044918|4|57118448
Lápiz de color suelto Prismacolor Verde pasto|7507694071765|7507694071765|1|95dddbdc
Lápiz de color suelto Prismacolor Verde olivo|||12|f4f76337
Lápiz de color suelto Prismacolor Verde limón|7509019665741|7509019665741|2|dd99aba7
Lápiz de color suelto Prismacolor Café oscuro|||0|c60e31bb
Lápiz de color suelto Prismacolor Siena tostada|7506729362519|7506729362519|6|5211c8f7
Lápiz de color suelto Prismacolor Ocre|7500352891095|7500352891095|0|97544c28
Lápiz de color suelto Prismacolor Durazno|7506659067096|7506659067096|4|562ec6a9
Lápiz de color suelto Prismacolor Carne|7504879193373|7504879193373|2|b0af8269
Lápiz de color suelto Mapita Blanco|7501134466944|7501134466944|0|ba57b406
Lápiz de color suelto Mapita Negro|7501260246137|7501260246137|2|e459828a
Lápiz de color suelto Mapita Gris frío|7505187553453|7505187553453|0|63003e3a
Lápiz de color suelto Mapita Amarillo canario|7503525513053|7503525513053|2|f2332cc7
Lápiz de color suelto Mapita Amarillo limón|7509408931884|7509408931884|2|c38a617b
Lápiz de color suelto Mapita Naranja|7508539045880|7508539045880|1|c9905df6
Lápiz de color suelto Mapita Rojo carmín|7506042355991|7506042355991|4|c1886345
Lápiz de color suelto Mapita Rojo escarlata|7503946935663|7503946935663|6|1a618f9a
Lápiz de color suelto Mapita Rosa|7500431301866|7500431301866|3|b755ba86
Lápiz de color suelto Mapita Magenta|7506566698451|7506566698451|6|931ffd42
Lápiz de color suelto Mapita Violeta|7507460868469|7507460868469|11|231963b4
Lápiz de color suelto Mapita Lila|7504369620129|7504369620129|5|cb668426
Lápiz de color suelto Mapita Azul ultramar|7504604410294|7504604410294|0|238a0755
Lápiz de color suelto Mapita Azul cielo|7501376174126|7501376174126|0|7a3b2b71
Lápiz de color suelto Mapita Azul verdoso|7503958432983|7503958432983|3|eb313d9a
Lápiz de color suelto Mapita Turquesa|7500478620333|7500478620333|0|6ef58726
Lápiz de color suelto Mapita Verde pasto|7501304545721|7501304545721|2|d5666c28
Lápiz de color suelto Mapita Verde olivo|7507233641909|7507233641909|2|697b7919
Lápiz de color suelto Mapita Verde limón|7504191164488|7504191164488|2|521dc189
Lápiz de color suelto Mapita Café oscuro|7507117355670|7507117355670|2|e20cd819
Lápiz de color suelto Mapita Siena tostada|7507447720988|7507447720988|2|1a4fff2b
Lápiz de color suelto Mapita Ocre|7501511382041|7501511382041|3|43a00d06
Lápiz de color suelto Mapita Durazno|7503176246355|7503176246355|0|0d908bf5
Lápiz de color suelto Mapita Carne|7502666204127|7502666204127|4|eaffcdb5
Plumón punto fino Stabilo Blanco|7502840345141|7502840345141|2|45217197
Plumón punto fino Stabilo Amarillo|7503009136068|7503009136068|1|9ba53d97
Plumón punto fino Stabilo Rosa|7509763934230|7509763934230|0|b176e897
Plumón punto fino Stabilo Azul|7501230696160|7501230696160|2|a00d1b77
Plumón punto fino Stabilo Verde|||0|91d58d09
Plumón punto fino Stabilo Rojo|7501383058280|7501383058280|1|ba92b837
Plumón punto fino Stabilo Naranja|7506520354843|7506520354843|3|c8e0fb47
Plumón punto fino Stabilo Negro|7509247581998|7509247581998|5|dae7705b
Plumón punto fino Stabilo Morado|7509180807322|7509180807322|2|bcdb4e37
Plumón punto fino Stabilo Café|7503783237005|7503783237005|1|0d2c2653
Plumón punto fino Stabilo Gris|7500804409229|7500804409229|0|b3fd1e57
Plumón punto fino Stabilo Dorado|7505108125059|7505108125059|1|728240d7
Plumón punto fino Stabilo Plateado|7505774962668|7505774962668|1|fc9d1f31
Plumón punto fino Stabilo Turquesa|7502203409817|7502203409817|0|dc941437
Plumón punto fino Stabilo Lila|7506547330783|7506547330783|0|c587b237
Plumón punto fino Stabilo Verde limón|7503262725122|7503262725122|1|9d06b818
Plumón punto fino Stabilo Azul cielo|7504871061595|7504871061595|1|58be79c0
Plumón punto fino Stabilo Rosa mexicano|7501607070630|7501607070630|0|2b689884
Plumón punto fino Stabilo Beige|7505503406388|7505503406388|1|c058de09
Plumón punto fino Stabilo Vino|7501481420989|7501481420989|1|90db7cf7
Plumón punto fino Pelikan Blanco|7507131551508|7507131551508|1|1d06950f
Plumón punto fino Pelikan Amarillo|7504736329549|7504736329549|0|ecdbe50f
Plumón punto fino Pelikan Rosa|7502085984075|7502085984075|0|c2a8880f
Plumón punto fino Pelikan Azul|7503280492778|7503280492778|0|b13ebaef
Plumón punto fino Pelikan Verde|7509642941953|7509642941953|1|1d1d2e81
Plumón punto fino Pelikan Rojo|||0|cbc457af
Plumón punto fino Pelikan Naranja|7509998709603|7509998709603|0|3c0b20bf
Plumón punto fino Pelikan Negro|7504588043822|7504588043822|0|662f11d3
Plumón punto fino Pelikan Morado|7507769859403|7507769859403|1|94c071af
Plumón punto fino Pelikan Café|7508160171880|7508160171880|0|1e5dc5cb
Plumón punto fino Pelikan Gris|7505258061092|7505258061092|0|c52ebdcf
Plumón punto fino Pelikan Dorado|7505946044307|7505946044307|0|4a67644f
Plumón punto fino Pelikan Plateado|7500880180531|7500880180531|0|215c6ab9
Plumón punto fino Pelikan Turquesa|7504880842796|7504880842796|1|2dcabbaf
Plumón punto fino Pelikan Lila|7502910438896|7502910438896|1|d6b951af
Plumón punto fino Pelikan Verde limón|7503575873824|7503575873824|0|020df3a0
Plumón punto fino Pelikan Azul cielo|7503855720800|7503855720800|0|c7eef748
Plumón punto fino Pelikan Rosa mexicano|||0|fbd6d00c
Plumón punto fino Pelikan Beige|7503555982690|7503555982690|1|4ba07f81
Plumón punto fino Pelikan Vino|7509119920771|7509119920771|0|a20d1c6f
Hojas blancas Scribe Carta|7507550170366|7507550170366|5|cd62b1d8
Hojas blancas Scribe Oficio|7509239895027|7509239895027 7505383027451|8|2accbe52
Hojas blancas HP Carta|7503387347445|7503387347445|1|ab9347f8
Hojas blancas HP Oficio|7502585636078|7502585636078|2|3f304c72
Hojas blancas Chamex Carta|7505160981778|7505160981778|3|38af0b98
Hojas blancas Chamex Oficio|7501124605377|7501124605377|8|f469a812
Hojas blancas Copamex Carta|7500823997813|7500823997813 7505406396656|2|198b9b36
Hojas blancas Copamex Oficio|7502491684804|7502491684804 7508630598285|1|1060c830
Hojas de color Scribe Amarillo|7509416310374|7509416310374|0|26646462
Hojas de color Scribe Rosa|7507083489652|7507083489652 7502930828004|2|c3a30a62
Hojas de color Scribe Verde|7504029010390|7504029010390|1|c9806814
Hojas de color Scribe Azul|7505495550885|7505495550885 7508421684012|1|b2393d42
Hojas de color Scribe Naranja|||3|2a4158d2
Hojas de color Scribe Rojo|7501659321353|7501659321353|0|ccbeda02
Hojas de color Scribe Morado|7503644076231|7503644076231 7503112074837|0|b53b9282
Hojas de color Scribe Canario|||0|2a595b0b
Hojas de color Scribe Lila|7504242732116|7504242732116 7507563570917|5|d7b3d402
Hojas de color Scribe Verde limón|7507610273808|7507610273808 7504074810587|0|c774332d
Hojas de color Copamex Amarillo|7507690863913|7507690863913|1|a1bda884
Hojas de color Copamex Rosa|7504067616479|7504067616479|0|63cb9084
Hojas de color Copamex Verde|7503882739011|7503882739011|0|019fedb6
Hojas de color Copamex Azul|7508062507879|7508062507879 7500241112577|6|5261c364
Hojas de color Copamex Naranja|7503954325951|7503954325951|2|96013d74
Hojas de color Copamex Rojo|7509783465950|7509783465950 7507162926849|0|6ce76024
Hojas de color Copamex Morado|7503172797240|7503172797240|1|639037a4
Hojas de color Copamex Canario|7502955492648|7502955492648 7501227928649|0|11a4015f
Hojas de color Copamex Lila|7509448023587|7509448023587|1|77dc5a24
Hojas de color Copamex Verde limón|7505667646743|7505667646743 7505750591301|10|2dbe364b
Papel opalina Copamex Carta 120 g|7504165297037|7504165297037|1|ea1ba6d0
Papel opalina Copamex Carta 225 g|7507769465703|7507769465703|2|e999f690
Papel opalina Scribe Carta 120 g|7504341641005|7504341641005 7509508056340|0|5235c6f2
Papel opalina Scribe Carta 225 g|7500207795660|7500207795660|1|51b416b2
Cartulina Blanco|||4|9de6779b
Cartulina Amarillo|P000001|P000001|6|5eafa19b
Cartulina Rosa|||0|5a97909b
Cartulina Azul|P000002|P000002|0|492dc37b
Cartulina Verde|||9|756c440d
Cartulina Rojo|P000003|P000003|6|63b3603b
Cartulina Naranja|||5|047c904b
Cartulina Negro|P000004|P000004|3|be7e275f
Cartulina Morado|||3|15a0543b
Cartulina Café|||7|b64cce57
Cartulina Gris|P000005|P000005|3|5d1dc65b
Cartulina Turquesa|||2|9f9e783b
Cartulina fluorescente Verde|P000006|P000006|2|9305f8c7
Cartulina fluorescente Rosa|P000007|P000007|0|2eab2bd5
Cartulina fluorescente Naranja|||0|a0595805
Cartulina fluorescente Amarilla|||1|207f40a3
Papel crepé Blanco|||2|452b77f7
Papel crepé Amarillo|P000008|P000008|0|e08513f7
Papel crepé Rosa|P000009|P000009|1|79431ef7
Papel crepé Azul|P000010|P000010|1|67d951d7
Papel crepé Verde|P000011|P000011|1|976dab69
Papel crepé Rojo|||5|825eee97
Papel crepé Naranja|P000012|P000012|10|ee82e9a7
Papel crepé Negro|||0|e07f8ebb
Papel crepé Morado|||2|bce55497
Papel crepé Café|||3|d4f85cb3
Papel crepé Gris|P000013|P000013|3|7bc954b7
Papel crepé Dorado|P000014|P000014|4|728c4737
Papel crepé Plateado|P000015|P000015|1|fdd3e4d1
Papel crepé Turquesa|||0|2173ea97
Papel china Blanco|P000016|P000016|0|4662257b
Papel china Amarillo|P000017|P000017|4|e3e15f7b
Papel china Rosa|P000018|P000018|2|b8d52e7b
Papel china Azul|P000019|P000019|1|a76b615b
Papel china Verde|P000020|P000020|0|3ab9e9ed
Papel china Rojo|||3|c1f0fe1b
Papel china Naranja|||1|7cc4462b
Papel china Negro|||2|83cbcd3f
Papel china Morado|P000021|P000021|1|be1c021b
Papel china Café|||0|148a6c37
Papel china Gris|P000022|P000022|4|bb5b643b
Papel china Dorado|P000023|P000023|0|73c2f4bb
Papel china Plateado|||0|2372e7cd
Papel china Turquesa|||0|24d0361b
Papel lustre Blanco|P000024|P000024|2|3effddf5
Papel lustre Amarillo|||2|6d9a4af5
Papel lustre Rosa|||2|c4c633f5
Papel lustre Azul|||5|b35c66d5
Papel lustre Verde|P000025|P000025|0|0e7f78e7
Papel lustre Rojo|||3|cde20395
Papel lustre Naranja|||0|c4d5c825
Papel lustre Negro|P000026|P000026|0|57915c39
Papel lustre Morado|||0|b6b9ba95
Papel lustre Café|||1|207b71b1
Papel lustre Gris|||1|c74c69b5
Papel lustre Dorado|||0|6c60ad35
Papel lustre Plateado|P000027|P000027|6|3e8c3e93
Papel lustre Turquesa|P000028|P000028|3|ae892195
Papel América Blanco|P000029|P000029|0|eab3c0f9
Papel América Amarillo|P000030|P000030|5|86dd0bf9
Papel América Rosa|P000031|P000031|1|9ccc38f9
Papel América Azul|P000032|P000032|0|8b626bd9
Papel América Verde|P000033|P000033|0|fd184ceb
Papel América Rojo|||0|a5e80899
Papel América Naranja|||1|531cfa29
Papel América Negro|P000034|P000034|0|462a303d
Papel kraft Natural|||0|b87426a0
Foamy Carta · Blanco|P000035|P000035|1|fdbae1c1
Foamy Carta · Amarillo|P000036|P000036|1|8c5688c1
Foamy Carta · Rosa|||2|cdd2fdc1
Foamy Carta · Azul|P000037|P000037|2|bc6930a1
Foamy Carta · Verde|||0|0780ffb3
Foamy Carta · Rojo|P000038|P000038|3|d6eecd61
Foamy Carta · Naranja|||5|c0df08f1
Foamy Carta · Negro|P000039|P000039|4|5092e305
Foamy Carta · Morado|||3|7574be61
Foamy Carta · Café|||0|29883b7d
Foamy Carta · Gris|P000040|P000040|1|d0593381
Foamy Carta · Dorado|||2|2b1bb101
Foamy Carta · Plateado|||0|5731b447
Foamy Carta · Turquesa|P000041|P000041|0|cd455f61
Foamy Pliego · Blanco|||5|a7c302fb
Foamy Pliego · Amarillo|P000042|P000042|7|c6977cfb
Foamy Pliego · Rosa|P000043|P000043|9|1c40cbfb
Foamy Pliego · Azul|P000044|P000044|5|0ad6fedb
Foamy Pliego · Verde|P000045|P000045|1|71b4276d
Foamy Pliego · Rojo|||3|255c9b9b
Foamy Pliego · Naranja|||5|0963c3ab
Foamy Pliego · Negro|P000046|P000046|3|bac60abf
Foamy Pliego · Morado|||7|1f7cdf9b
Foamy Pliego · Café|P000047|P000047|7|77f609b7
Foamy Pliego · Gris|P000048|P000048|0|1ec701bb
Foamy Pliego · Dorado|||0|d523d23b
Foamy Pliego · Plateado|||5|ee2dba4d
Foamy Pliego · Turquesa|P000049|P000049|4|0786539b
Foamy diamantado Blanco|P000050|P000050|1|cceef827
Foamy diamantado Amarillo|||3|dd2cfc27
Foamy diamantado Rosa|||0|d9ae3727
Foamy diamantado Azul|||0|c8446a07
Foamy diamantado Verde|||0|89737799
Foamy diamantado Rojo|P000051|P000051|0|e2ca06c7
Foamy diamantado Naranja|P000052|P000052|4|93671dd7
Foamy diamantado Negro|P000053|P000053|1|d2855aeb
Foamy diamantado Morado|P000054|P000054|1|44a8d4c7
Foamy diamantado Café|||0|356374e3
Forro adherible Con-Tact Transparente|7505002733152|7505002733152|3|3e945bfd
Forro adherible Con-Tact Cuadros|7500480806114|7500480806114|0|a02998ae
Forro adherible Con-Tact Estampado|||5|d62247ce
Forro adherible Plastifilm Transparente|||2|360d0d7d
Forro adherible Plastifilm Cuadros|7508534562764|7508534562764|3|921cb172
Forro adherible Plastifilm Estampado|7509557682903|7509557682903|2|f3935e92
Forro de plástico para libros Transparente|P000055|P000055|3|6277063a
Forro de plástico para libros Azul|P000056|P000056|6|ec7c441a
Papel fotográfico HP Carta brillante|7501721783034|7501721783034|0|133f5f96
Papel fotográfico HP Carta mate|7500049072783|7500049072783 7504590411763|0|065c11a0
Papel fotográfico HP 4 × 6|7503418756741|7503418756741|0|abaea730
Papel fotográfico Epson Carta brillante|7505104846439|7505104846439|0|f84ddfe6
Papel fotográfico Epson Carta mate|7505971100399|7505971100399|0|0d2fb5f0
Papel fotográfico Epson 4 × 6|7502716472803|7502716472803|1|85b0ece0
Papel para envolver Cumpleaños|||0|ff0134f4
Papel para envolver Navidad|||1|4f253fd2
Papel para envolver Bebé|||4|7454437e
Papel para envolver Liso rojo|P000057|P000057|0|c46a50bb
Papel para envolver Liso azul|||0|a9e4b3fb
Papel para envolver Kraft|P000058|P000058|1|f0e22e20
Cuaderno profesional raya Scribe 100 hojas · Lisa|7509168733704|7509168733704|10|775da301
Cuaderno profesional raya Scribe 100 hojas · Diseño infantil|7506311602054|7506311602054|3|f59f4e1d
"""

    private const val PRODUCTOS_2 = """
Cuaderno profesional raya Scribe 100 hojas · Diseño juvenil|7506253374194|7506253374194|7|1f27c60b
Cuaderno profesional raya Scribe 100 hojas · Diseño casual|7505601780557|7505601780557|11|463716a1
Cuaderno profesional raya Scribe 200 hojas · Lisa|7509901294363|7509901294363|0|4f1e29a1
Cuaderno profesional raya Scribe 200 hojas · Diseño infantil|||7|b7a1537d
Cuaderno profesional raya Scribe 200 hojas · Diseño juvenil|7509322009515|7509322009515|13|b2aba36b
Cuaderno profesional raya Scribe 200 hojas · Diseño casual|7500155052341|7500155052341|5|51d2cc01
Cuaderno profesional raya Norma 100 hojas · Lisa|7507262995233|7507262995233|5|4463a5ed
Cuaderno profesional raya Norma 100 hojas · Diseño infantil|7505729462199|7505729462199|5|3666c1b1
Cuaderno profesional raya Norma 100 hojas · Diseño juvenil|7503446841419|7503446841419|5|e09f0e9f
Cuaderno profesional raya Norma 100 hojas · Diseño casual|7504303165914|7504303165914|12|2c4f7435
Cuaderno profesional raya Norma 200 hojas · Lisa|7505876974026|7505876974026|11|1c242c8d
Cuaderno profesional raya Norma 200 hojas · Diseño infantil|7503877576904|7503877576904|7|f868c711
Cuaderno profesional raya Norma 200 hojas · Diseño juvenil|7508039005391|7508039005391|6|7422ebff
Cuaderno profesional raya Norma 200 hojas · Diseño casual|7507719583334|7507719583334|3|37eb2995
Cuaderno profesional raya Estrella 100 hojas · Lisa|7502856847431|7502856847431|11|a2add4fd
Cuaderno profesional raya Estrella 100 hojas · Diseño infantil|7504150639651|7504150639651|0|6e30a0a1
Cuaderno profesional raya Estrella 100 hojas · Diseño juvenil|7509078246097|7509078246097|4|2a33e98f
Cuaderno profesional raya Estrella 100 hojas · Diseño casual|7509277221932|7509277221932|2|363e4b25
Cuaderno profesional raya Estrella 200 hojas · Lisa|7503050413958|7503050413958|3|7a6e5b9d
Cuaderno profesional raya Estrella 200 hojas · Diseño infantil|7503436749916|7503436749916|8|3032a601
Cuaderno profesional raya Estrella 200 hojas · Diseño juvenil|7507936150708|7507936150708|5|bdb7c6ef
Cuaderno profesional raya Estrella 200 hojas · Diseño casual|7501791542463|7501791542463|3|41da0085
Cuaderno profesional raya Jean Book 100 hojas · Lisa|7503645279792|7503645279792|2|981cea33
Cuaderno profesional raya Jean Book 100 hojas · Diseño infantil|7506634521742|7506634521742|2|14ee0eab
Cuaderno profesional raya Jean Book 100 hojas · Diseño juvenil|7508394018036|7508394018036|1|98a95219
Cuaderno profesional raya Jean Book 100 hojas · Diseño casual|7508276577668|7508276577668|2|37284e2f
Cuaderno profesional raya Jean Book 200 hojas · Lisa|7504322410835|7504322410835|3|6fdd70d3
Cuaderno profesional raya Jean Book 200 hojas · Diseño infantil|7503501417474|7503501417474|0|d6f0140b
Cuaderno profesional raya Jean Book 200 hojas · Diseño juvenil|7506946476679|7506946476679|5|2c2d2f79
Cuaderno profesional raya Jean Book 200 hojas · Diseño casual|7500261285138|7500261285138|9|42c4038f
Cuaderno profesional cuadro chico Scribe 100 hojas · Lisa|7502424481173|7502424481173|13|dbf3ff0d
Cuaderno profesional cuadro chico Scribe 100 hojas · Diseño infantil|7505044592625|7505044592625|7|27b42491
Cuaderno profesional cuadro chico Scribe 100 hojas · Diseño juvenil|7505986282950|7505986282950|1|f1a7a97f
Cuaderno profesional cuadro chico Scribe 100 hojas · Diseño casual|7504122322819|7504122322819|2|02814715
Cuaderno profesional cuadro chico Scribe 200 hojas · Lisa|7502555218617|7502555218617|11|b3b485ad
Cuaderno profesional cuadro chico Scribe 200 hojas · Diseño infantil|7500929395148|7500929395148|15|e9b629f1
Cuaderno profesional cuadro chico Scribe 200 hojas · Diseño juvenil|7504608755155|7504608755155|23|852b86df
Cuaderno profesional cuadro chico Scribe 200 hojas · Diseño casual|7502288448060|7502288448060|23|0e1cfc75
Cuaderno profesional cuadro chico Norma 100 hojas · Lisa|||8|e6b994f9
Cuaderno profesional cuadro chico Norma 100 hojas · Diseño infantil|7505943018387|7505943018387|5|08e6a525
Cuaderno profesional cuadro chico Norma 100 hojas · Diseño juvenil|7509704351553|7509704351553|9|9ce93f13
Cuaderno profesional cuadro chico Norma 100 hojas · Diseño casual|7508013072197|7508013072197|6|6a9331a9
Cuaderno profesional cuadro chico Norma 200 hojas · Lisa|||1|be7a1b99
Cuaderno profesional cuadro chico Norma 200 hojas · Diseño infantil|7506417240907|7506417240907|0|cae8aa85
Cuaderno profesional cuadro chico Norma 200 hojas · Diseño juvenil|7506151521843|7506151521843|9|306d1c73
Cuaderno profesional cuadro chico Norma 200 hojas · Diseño casual|||5|762ee709
Cuaderno profesional cuadro chico Estrella 100 hojas · Lisa|7500442738712|7500442738712|4|dd5f4b09
Cuaderno profesional cuadro chico Estrella 100 hojas · Diseño infantil|7503336494916|7503336494916|2|444d1d15
Cuaderno profesional cuadro chico Estrella 100 hojas · Diseño juvenil|7509709235858|7509709235858|15|2a6cf303
Cuaderno profesional cuadro chico Estrella 100 hojas · Diseño casual|7504760913608|7504760913608|2|68532199
Cuaderno profesional cuadro chico Estrella 200 hojas · Lisa|7505374384563|7505374384563|4|b51fd1a9
Cuaderno profesional cuadro chico Estrella 200 hojas · Diseño infantil|7502054066269|7502054066269|2|064f2275
Cuaderno profesional cuadro chico Estrella 200 hojas · Diseño juvenil|7506879967794|7506879967794|8|bdf0d063
Cuaderno profesional cuadro chico Estrella 200 hojas · Diseño casual|7508942876675|7508942876675|21|73eed6f9
Cuaderno profesional cuadro chico Jean Book 100 hojas · Lisa|7501439580376|7501439580376|3|c97528a7
Cuaderno profesional cuadro chico Jean Book 100 hojas · Diseño infantil|7500544376416|7500544376416|3|71e4cdb7
Cuaderno profesional cuadro chico Jean Book 100 hojas · Diseño juvenil|7502507582285|7502507582285|7|861c6425
Cuaderno profesional cuadro chico Jean Book 100 hojas · Diseño casual|||9|3e10733b
Cuaderno profesional cuadro chico Jean Book 200 hojas · Lisa|7507051487048|7507051487048|7|a135af47
Cuaderno profesional cuadro chico Jean Book 200 hojas · Diseño infantil|7504873914059|7504873914059|6|33e6d317
Cuaderno profesional cuadro chico Jean Book 200 hojas · Diseño juvenil|7509215568662|7509215568662|1|19a04185
Cuaderno profesional cuadro chico Jean Book 200 hojas · Diseño casual|||2|49ac289b
Cuaderno profesional cuadro grande Scribe 100 hojas · Lisa|7509227222880|7509227222880|6|84b4f6ed
Cuaderno profesional cuadro grande Scribe 100 hojas · Diseño infantil|7505037095607|7505037095607|5|a92a50b1
Cuaderno profesional cuadro grande Scribe 100 hojas · Diseño juvenil|7509812097633|7509812097633|9|fcfa5d9f
Cuaderno profesional cuadro grande Scribe 100 hojas · Diseño casual|7507054070049|7507054070049|1|f5328335
Cuaderno profesional cuadro grande Scribe 200 hojas · Lisa|7504300426742|7504300426742|3|5c757d8d
Cuaderno profesional cuadro grande Scribe 200 hojas · Diseño infantil|7502805869699|7502805869699|4|6b2c5611
Cuaderno profesional cuadro grande Scribe 200 hojas · Diseño juvenil|7508901364809|7508901364809|1|907e3aff
Cuaderno profesional cuadro grande Scribe 200 hojas · Diseño casual|7509645718521|7509645718521|3|00ce3895
Cuaderno profesional cuadro grande Norma 100 hojas · Lisa|7507312584622|7507312584622|0|b11704d9
Cuaderno profesional cuadro grande Norma 100 hojas · Diseño infantil|7504260311614|7504260311614|7|14395945
Cuaderno profesional cuadro grande Norma 100 hojas · Diseño juvenil|7502673374943|7502673374943|4|8f9a7b33
Cuaderno profesional cuadro grande Norma 100 hojas · Diseño casual|7503937343279|7503937343279|2|1aa4f5c9
Cuaderno profesional cuadro grande Norma 200 hojas · Lisa|7509302211631|7509302211631|1|88d78b79
Cuaderno profesional cuadro grande Norma 200 hojas · Diseño infantil|7501930948040|7501930948040|1|d63b5ea5
Cuaderno profesional cuadro grande Norma 200 hojas · Diseño juvenil|7508829555785|7508829555785|6|231e5893
Cuaderno profesional cuadro grande Norma 200 hojas · Diseño casual|7503049440408|7503049440408|0|2640ab29
Cuaderno profesional cuadro grande Estrella 100 hojas · Lisa|7508347336996|7508347336996|1|cb2152e9
Cuaderno profesional cuadro grande Estrella 100 hojas · Diseño infantil|7501475662074|7501475662074|3|88903935
Cuaderno profesional cuadro grande Estrella 100 hojas · Diseño juvenil|7503838181994|7503838181994|2|27089723
Cuaderno profesional cuadro grande Estrella 100 hojas · Diseño casual|7507702161044|7507702161044|0|e9c94db9
Cuaderno profesional cuadro grande Estrella 200 hojas · Lisa|7502533233205|7502533233205|4|a2e1d989
Cuaderno profesional cuadro grande Estrella 200 hojas · Diseño infantil|7502357597712|7502357597712|0|4a923e95
Cuaderno profesional cuadro grande Estrella 200 hojas · Diseño juvenil|7502662054771|7502662054771|1|ba8c7483
Cuaderno profesional cuadro grande Estrella 200 hojas · Diseño casual|7506722123292|7506722123292|5|f5650319
Cuaderno forma italiana Scribe Raya · Lisa|7502160425837|7502160425837|6|6f0b0cd6
Cuaderno forma italiana Scribe Raya · Diseño infantil|7506646505099|7506646505099|3|fc37a4a8
Cuaderno forma italiana Scribe Raya · Diseño juvenil|||12|6c6a4356
Cuaderno forma italiana Scribe Cuadro · Lisa|7505806125405|7505806125405|2|b9d6e58c
Cuaderno forma italiana Scribe Cuadro · Diseño infantil|7501172388963|7501172388963|2|36b45f32
Cuaderno forma italiana Scribe Cuadro · Diseño juvenil|7508752512022|7508752512022|2|d7081860
Cuaderno forma italiana Scribe Doble raya · Lisa|7505430646994|7505430646994|4|52d5aaea
Cuaderno forma italiana Scribe Doble raya · Diseño infantil|7508315362323|7508315362323|6|1b7a7014
Cuaderno forma italiana Scribe Doble raya · Diseño juvenil|7506376626767|7506376626767|4|b680f9c2
Cuaderno forma italiana Norma Raya · Lisa|7508563063744|7508563063744|4|f12ee7ea
Cuaderno forma italiana Norma Raya · Diseño infantil|7500796856476|7500796856476|0|c80c9314
Cuaderno forma italiana Norma Raya · Diseño juvenil|7500965626695|7500965626695|2|973fdcc2
Cuaderno forma italiana Norma Cuadro · Lisa|7501968120869|7501968120869|4|81af16a0
Cuaderno forma italiana Norma Cuadro · Diseño infantil|7507614834630|7507614834630|5|1854379e
Cuaderno forma italiana Norma Cuadro · Diseño juvenil|7503175957566|7503175957566|4|015b1bcc
Cuaderno forma italiana Norma Doble raya · Lisa|7501001946852|7501001946852|1|3b7133fe
Cuaderno forma italiana Norma Doble raya · Diseño infantil|7506677043355|7506677043355|5|3a13f080
Cuaderno forma italiana Norma Doble raya · Diseño juvenil|||8|e017a52e
Cuaderno forma italiana Estrella Raya · Lisa|7507371951793|7507371951793|3|ba6d165a
Cuaderno forma italiana Estrella Raya · Diseño infantil|7505714870329|7505714870329|9|016926a4
Cuaderno forma italiana Estrella Raya · Diseño juvenil|7505167684689|7505167684689|3|82cc1452
Cuaderno forma italiana Estrella Cuadro · Lisa|7509360786201|7509360786201|0|0a508d10
Cuaderno forma italiana Estrella Cuadro · Diseño infantil|7503476962290|7503476962290|0|631c832e
Cuaderno forma italiana Estrella Cuadro · Diseño juvenil|7508379957817|7508379957817|0|55e10b5c
Cuaderno forma italiana Estrella Doble raya · Lisa|7500412690033|7500412690033|10|f8b7ca6e
Cuaderno forma italiana Estrella Doble raya · Diseño infantil|7509984751807|7509984751807|3|18731c10
Cuaderno forma italiana Estrella Doble raya · Diseño juvenil|7500026136767|7500026136767|0|806c74be
Cuaderno forma francesa Scribe Raya · Lisa|7505874572675|7505874572675|0|82224166
Cuaderno forma francesa Scribe Raya · Diseño infantil|7505774156203|7505774156203|5|71264e18
Cuaderno forma francesa Scribe Raya · Diseño juvenil|7509155080873|7509155080873|1|d2d488c6
Cuaderno forma francesa Scribe Cuadro chico · Lisa|7508365485539|7508365485539|4|de2b17da
Cuaderno forma francesa Scribe Cuadro chico · Diseño infantil|7503517436384|7503517436384|2|8a417524
Cuaderno forma francesa Scribe Cuadro chico · Diseño juvenil|7502287054118|7502287054118|4|8a6602d2
Cuaderno forma francesa Scribe Cuadro grande · Lisa|7504975116801|7504975116801|0|3c96447a
Cuaderno forma francesa Scribe Cuadro grande · Diseño infantil|7505115758035|7505115758035|6|e8fe1484
Cuaderno forma francesa Scribe Cuadro grande · Diseño juvenil|7508790734523|7508790734523|5|fc82fa32
Cuaderno forma francesa Scribe Blanco · Lisa|7509654898467|7509654898467|7|b8122b7e
Cuaderno forma francesa Scribe Blanco · Diseño infantil|7507015648157|7507015648157|6|0b148900
Cuaderno forma francesa Scribe Blanco · Diseño juvenil|7507048178423|7507048178423|6|d1645dae
Cuaderno forma francesa Norma Raya · Lisa|7509527424311|7509527424311|1|76aa007a
Cuaderno forma francesa Norma Raya · Diseño infantil|7506476877618|7506476877618|0|2e76d884
Cuaderno forma francesa Norma Raya · Diseño juvenil|7500658028577|7500658028577|0|115cbe32
Cuaderno forma francesa Norma Cuadro chico · Lisa|7505091978175|7505091978175|0|caebdaee
Cuaderno forma francesa Norma Cuadro chico · Diseño infantil|7503733748834|7503733748834|4|25747b90
Cuaderno forma francesa Norma Cuadro chico · Diseño juvenil|||10|753fb43e
Cuaderno forma francesa Norma Cuadro grande · Lisa|7506248915654|7506248915654|8|fc32928e
Cuaderno forma francesa Norma Cuadro grande · Diseño infantil|7501272402026|7501272402026|5|83892ff0
Cuaderno forma francesa Norma Cuadro grande · Diseño juvenil|7509178626010|7509178626010|4|97b6009e
Cuaderno forma francesa Norma Blanco · Lisa|7502198413585|7502198413585|5|95bc4092
Cuaderno forma francesa Norma Blanco · Diseño infantil|7506783517573|7506783517573|4|3f01fd6c
Cuaderno forma francesa Norma Blanco · Diseño juvenil|7500559384444|7500559384444|0|7cbbfd1a
Cuaderno forma francesa Estrella Raya · Lisa|7502372763024|7502372763024|2|4eb782ea
Cuaderno forma francesa Estrella Raya · Diseño infantil|7508775130661|7508775130661|5|a3059814
Cuaderno forma francesa Estrella Raya · Diseño juvenil|7504255882341|7504255882341|7|76b621c2
Cuaderno forma francesa Estrella Cuadro chico · Lisa|7506005668953|7506005668953|3|e8350d5e
Cuaderno forma francesa Estrella Cuadro chico · Diseño infantil|7500067908248|7500067908248|4|b4a18b20
Cuaderno forma francesa Estrella Cuadro chico · Diseño juvenil|7504578616159|7504578616159|5|a88b67ce
Cuaderno forma francesa Estrella Cuadro grande · Lisa|7507088692873|7507088692873|6|ebf8e8fe
Cuaderno forma francesa Estrella Cuadro grande · Diseño infantil|7508946591260|7508946591260|2|fbb09b80
Cuaderno forma francesa Estrella Cuadro grande · Diseño juvenil|7506455306115|7506455306115|0|26e3102e
Cuaderno forma francesa Estrella Blanco · Lisa|7503229374448|7503229374448|0|cd630b02
Cuaderno forma francesa Estrella Blanco · Diseño infantil|7500326751578|7500326751578|0|480674fc
Cuaderno forma francesa Estrella Blanco · Diseño juvenil|7500246279954|7500246279954|0|089918aa
Cuaderno de dibujo Scribe Profesional|7504192285540|7504192285540|0|e286d62b
Cuaderno de dibujo Scribe Francés|7501158298750|7501158298750|0|48bde82f
Cuaderno de dibujo Norma Profesional|7506814888948|7506814888948|0|6865aeff
Cuaderno de dibujo Norma Francés|7504120491654|7504120491654|0|ad9cd503
Block de hojas Scribe Raya|7500112426185|7500112426185|2|e07a37eb
Block de hojas Scribe Cuadro|7506799214015|7506799214015|1|6344af21
Block de hojas Scribe Blanco|7501783284340|7501783284340|1|2d888c83
Block de hojas Estrella Raya|7508376666057|7508376666057|1|202c452b
Block de hojas Estrella Cuadro|7506303647469|7506303647469|1|34ae1c61
Block de hojas Estrella Blanco|7502380890866|7502380890866|1|fef1f9c3
Cuaderno pautado de música Scribe|7509660685952|7509660685952|0|9d1aa7f8
Agenda Norma 2026|7505598851339|7505598851339|0|0517ddfc
Agenda Norma 2027|7508104740974|7508104740974|0|39fe193e
Pintura acrílica 20 ml Politec Blanco|7509816146344|7509816146344|4|c08518b5
Pintura acrílica 20 ml Politec Amarillo|7503231696354|7503231696354|4|e08425b5
Pintura acrílica 20 ml Politec Rosa|7506848327642|7506848327642|3|a996ceb5
Pintura acrílica 20 ml Politec Azul|7506488989002|7506488989002|4|982d0195
Pintura acrílica 20 ml Politec Verde|7507462474132|7507462474132|14|019463a7
Pintura acrílica 20 ml Politec Rojo|7504770617480|7504770617480|1|b2b29e55
Pintura acrílica 20 ml Politec Naranja|7506500087648|7506500087648|0|f9f752e5
Pintura acrílica 20 ml Politec Negro|7500030194807|7500030194807|1|4aa646f9
Pintura acrílica 20 ml Politec Morado|7501065902665|7501065902665|2|383ef555
Pintura acrílica 20 ml Politec Café|7509384382113|7509384382113|0|054c0c71
Pintura acrílica 20 ml Politec Gris|7506627960329|7506627960329|4|ac1d0475
Pintura acrílica 20 ml Politec Dorado|7509222774285|7509222774285|1|ede5e7f5
Pintura acrílica 20 ml Politec Plateado|7501339785758|7501339785758|5|edae5bd3
Pintura acrílica 20 ml Politec Turquesa|7504553988462|7504553988462|4|2172fc55
Pintura acrílica 20 ml Politec Lila|7501309514661|7501309514661|0|bda79855
Pintura acrílica 20 ml Politec Verde limón|7502184938856|7502184938856|4|279e70ba
Pintura acrílica 20 ml Politec Azul cielo|7502638015027|7502638015027|0|e1cff2e2
Pintura acrílica 20 ml Politec Rosa mexicano|7502378534178|7502378534178|1|bc4f3026
Pintura acrílica 20 ml Politec Beige|7506011011910|7506011011910|1|3017b4a7
Pintura acrílica 20 ml Politec Vino|7507538981137|7507538981137|1|88fb6315
Pintura acrílica 20 ml Vinci Blanco|7501731898889|7501731898889|4|0b7cb80b
Pintura acrílica 20 ml Vinci Amarillo|7503207576529|7503207576529|3|9d502a0b
Pintura acrílica 20 ml Vinci Rosa|7507361824625|7507361824625|0|86bf890b
Pintura acrílica 20 ml Vinci Azul|7509842229189|7509842229189|0|7555bbeb
Pintura acrílica 20 ml Vinci Verde|||2|3777e07d
Pintura acrílica 20 ml Vinci Rojo|7507587800205|7507587800205|3|8fdb58ab
Pintura acrílica 20 ml Vinci Naranja|7509794596773|7509794596773|2|658474bb
Pintura acrílica 20 ml Vinci Negro|7509350008801|7509350008801|0|8089c3cf
Pintura acrílica 20 ml Vinci Morado|7509944459415|7509944459415|1|833694ab
Pintura acrílica 20 ml Vinci Café|7503631222085|7503631222085|1|e274c6c7
Pintura acrílica 20 ml Vinci Gris|7505471022115|7505471022115|0|8945becb
Pintura acrílica 20 ml Vinci Dorado|7509167104239|7509167104239|1|38dd874b
Pintura acrílica 20 ml Vinci Plateado|7503819875393|7503819875393|2|01aaa73d
Pintura acrílica 20 ml Vinci Turquesa|7508294815612|7508294815612|0|de3f00ab
Pintura acrílica 20 ml Vinci Lila|7500895597638|7500895597638|4|9ad052ab
Pintura acrílica 20 ml Vinci Verde limón|7505593574226|7505593574226|2|edfaa824
Pintura acrílica 20 ml Vinci Azul cielo|||3|c9c6fccc
Pintura acrílica 20 ml Vinci Rosa mexicano|7502385597609|7502385597609|0|018da290
Pintura acrílica 20 ml Vinci Beige|7500782583027|7500782583027|1|65fb317d
Pintura acrílica 20 ml Vinci Vino|7509037573257|7509037573257|0|66241d6b
Pintura acrílica 20 ml Pelikan Blanco|7504786316261|7504786316261|5|05069bd5
Pintura acrílica 20 ml Pelikan Amarillo|7502753485675|7502753485675|0|344f18d5
Pintura acrílica 20 ml Pelikan Rosa|7504378005894|7504378005894|2|6096e1d5
Pintura acrílica 20 ml Pelikan Azul|7507719845494|7507719845494|1|4f2d14b5
Pintura acrílica 20 ml Pelikan Verde|7508023439294|7508023439294|0|f8dc2ec7
Pintura acrílica 20 ml Pelikan Rojo|7505555418407|7505555418407|0|69b2b175
Pintura acrílica 20 ml Pelikan Naranja|7507369197387|7507369197387|6|24248e05
Pintura acrílica 20 ml Pelikan Negro|7504937012790|7504937012790|0|41ee1219
Pintura acrílica 20 ml Pelikan Morado|7500217530565|7500217530565|0|7cc07875
Pintura acrílica 20 ml Pelikan Café|7500363079901|7500363079901|8|bc4c1f91
Pintura acrílica 20 ml Pelikan Gris|7503677225804|7503677225804|2|631d1795
Pintura acrílica 20 ml Pelikan Dorado|7502706256338|7502706256338|2|32676b15
Pintura acrílica 20 ml Pelikan Plateado|7507805166632|7507805166632|0|395d3cb3
Pintura acrílica 20 ml Pelikan Turquesa|7506861460111|7506861460111|0|753def75
Pintura acrílica 20 ml Pelikan Lila|7505628412530|7505628412530|3|74a7ab75
Pintura acrílica 20 ml Pelikan Verde limón|7504518915816|7504518915816|0|8e2f919a
Pintura acrílica 20 ml Pelikan Azul cielo|7504554699664|7504554699664|3|c1f3cbc2
Pintura acrílica 20 ml Pelikan Rosa mexicano|7506429928480|7506429928480|0|41a4e106
Pintura acrílica 20 ml Pelikan Beige|||1|275f7fc7
Pintura acrílica 20 ml Pelikan Vino|7500319341984|7500319341984|0|3ffb7635
Pintura textil Politec Blanco|7503963853162|7503963853162|0|48b8b405
Pintura textil Politec Amarillo|7506694514692|7506694514692|0|a3d99905
Pintura textil Politec Rosa|7505072421515|7505072421515|0|2bfc9205
Pintura textil Politec Azul|7501866164118|7501866164118|0|1a92c4e5
Pintura textil Politec Verde|7506237596949|7506237596949|1|819692f7
Pintura textil Politec Rojo|7507482235652|7507482235652|0|351861a5
Pintura textil Politec Naranja|7506220834973|7506220834973|0|43b15a35
Pintura textil Politec Negro|7502860559962|7502860559962|0|caa87649
Pintura textil Politec Morado|7508853481166|7508853481166|0|c07290a5
Pintura textil Politec Café|||2|87b1cfc1
Pintura textil Politec Gris|7504634401569|7504634401569|0|2e82c7c5
Pintura textil Politec Dorado|7507494854872|7507494854872|1|76198345
Pintura textil Politec Plateado|7504201343155|7504201343155|0|6bee2a83
Pintura textil Politec Turquesa|7504870992265|7504870992265|0|e4c86fa5
Pintura textil Vinci Blanco|7504623324275|7504623324275|0|8de27b5b
Pintura textil Vinci Amarillo|7500590126096|7500590126096|0|2583c55b
Pintura textil Vinci Rosa|7507176301779|7507176301779|0|02ab745b
Pintura textil Vinci Azul|||0|f141a73b
Pintura textil Vinci Verde|7509141283981|7509141283981|0|681637cd
Pintura textil Vinci Rojo|7505641046118|7505641046118|0|0bc743fb
Pintura textil Vinci Naranja|7509039053085|7509039053085|4|e586a40b
Pintura textil Vinci Negro|7503594730689|7503594730689|0|b1281b1f
Pintura textil Vinci Morado|7508197491432|7508197491432|0|059c57fb
Pintura textil Vinci Café|7508886509448|7508886509448|0|5e60b217
Pintura textil Vinci Gris|7505758608360|7505758608360|0|0531aa1b
Pintura textil Vinci Dorado|7504866600440|7504866600440|0|bb434a9b
Pintura textil Vinci Plateado|7507753947406|7507753947406|0|cbfd4ded
Pintura textil Vinci Turquesa|7501309641626|7501309641626|0|66729bfb
Témpera Vinci Caja 6|7501669547439|7501669547439|3|b07f6832
Témpera Vinci Caja 12|7505512832260|7505512832260|3|85e81af4
Témpera Pelikan Caja 6|7508976974651|7508976974651|0|f15c5412
Témpera Pelikan Caja 12|7504657689487|7504657689487|1|03398ed4
Témpera Crayola Caja 6|7504431205834|7504431205834|1|562349f2
Témpera Crayola Caja 12|7508153818570|7508153818570|2|521a8cb4
Acuarelas Pelikan 12 colores|7504798804251|7504798804251|3|77bfa408
Acuarelas Pelikan 24 colores|7509208072725|7509208072725|7|6b978e4a
Acuarelas Vinci 12 colores|7502469769533|7502469769533|2|aa5351e8
Acuarelas Vinci 24 colores|7503251274402|7503251274402|0|9e2b3c2a
Acuarelas Crayola 12 colores|7506740765597|7506740765597|1|68bf3a28
Acuarelas Crayola 24 colores|7501335259741|7501335259741|1|5c97246a
Pincel Vinci No. 2|7502457939856|7502457939856|0|f40be864
Pincel Vinci No. 4|7504775062803|7504775062803|5|465e2be8
Pincel Vinci No. 6|7500933542866|7500933542866|3|98b06f6c
Pincel Vinci No. 8|7505894401863|7505894401863|1|eb02b2f0
Pincel Vinci No. 10|7501241945998|7501241945998|2|a10ab5e0
Pincel Vinci No. 12|7508076497036|7508076497036|3|d8f6dea0
Pincel Pelikan No. 2|||1|e698fa2e
Pincel Pelikan No. 4|7507605989424|7507605989424|0|38eb3db2
Pincel Pelikan No. 6|7506768004784|7506768004784|0|8b3d8136
Pincel Pelikan No. 8|7507771310381|7507771310381|0|dd8fc4ba
Pincel Pelikan No. 10|7508939846988|7508939846988|2|249a7d2a
Pincel Pelikan No. 12|7500551532447|7500551532447|2|5c86a5ea
Plastilina Pelikan Blanco|7503680717617|7503680717617|6|4ffda5eb
Plastilina Pelikan Amarillo|7509097259368|7509097259368|1|b9e727eb
Plastilina Pelikan Rosa|7508383223519|7508383223519|6|d5a266eb
Plastilina Pelikan Azul|7503205528711|7503205528711|0|c43899cb
Plastilina Pelikan Verde|7504922034936|7504922034936|0|591ac65d
Plastilina Pelikan Rojo|7507356068447|7507356068447|2|debe368b
Plastilina Pelikan Naranja|7502099510512|7502099510512|0|8d816a9b
Plastilina Pelikan Negro|7502118445467|7502118445467|2|a22ca9af
Plastilina Pelikan Morado|7506046505149|7506046505149|2|c7b7828b
Plastilina Pelikan Café|7501009019589|7501009019589|0|3157a4a7
Plastilina Vinci Blanco|7504437326885|7504437326885|1|80883d21
Plastilina Vinci Amarillo|7503115614870|7503115614870|2|e8473421
Plastilina Vinci Rosa|7509888073050|7509888073050|1|41550921
Plastilina Vinci Azul|7506902525854|7506902525854|0|2feb3c01
Plastilina Vinci Verde|7501612606756|7501612606756|2|a2adb313
Plastilina Vinci Rojo|||1|4a70d8c1
Plastilina Vinci Naranja|7505721106053|7505721106053|0|c5c30c51
Plastilina Vinci Negro|7506577810118|7506577810118|3|ebbf9665
Plastilina Vinci Morado|7505487016504|7505487016504|1|f84219c1
Plastilina Vinci Café|7509016224873|7509016224873|0|9d0a46dd
Plastilina Crayola Blanco|7501500608879|7501500608879|3|ac86c999
Plastilina Crayola Amarillo|7502120936717|7502120936717|2|a0bac499
Plastilina Crayola Rosa|7503114565579|7503114565579|1|75bc9199
Plastilina Crayola Azul|7501183399132|7501183399132|0|6452c479
Plastilina Crayola Verde|7509053165153|7509053165153|2|5b54fd8b
Plastilina Crayola Rojo|7501262167621|7501262167621|0|7ed86139
Plastilina Crayola Naranja|7509896156806|7509896156806|3|ec505ac9
Plastilina Crayola Negro|||4|a466e0dd
Plastilina Crayola Morado|7504611681694|7504611681694|3|2440a639
Plastilina Crayola Café|7508751754768|7508751754768|0|d171cf55
Diamantina sobre Dorada|P000059|P000059|5|68c94d1d
Diamantina sobre Plateada|P000060|P000060|0|ae0d102b
Diamantina sobre Roja|P000061|P000061|6|d273fbfd
Diamantina sobre Azul|P000062|P000062|0|23a56a6f
Diamantina sobre Verde|||0|9095fe01
Diamantina sobre Rosa|P000063|P000063|0|350f378f
Diamantina sobre Morada|||1|b3225a7d
Diamantina sobre Tornasol|||0|2cd81a38
Diamantina a granel Dorada|P000064|P000064|0|7046ce33
Diamantina a granel Plateada|P000065|P000065|0|963fb1d5
Diamantina a granel Roja|||0|3fd97813
Diamantina a granel Tornasol|||0|c1bb1e62
Silicón en barra Pegaso Delgada|7509395698395|7509395698395 7506017171632|3|61ea9765
Silicón en barra Pegaso Gruesa|7504775393396|7504775393396 7508977846728|2|af69edd7
Pistola de silicón Pegaso Chica|7507860331099|7507860331099|0|46cbb543
Pistola de silicón Pegaso Grande|7505584679114|7505584679114|0|4995bed3
Pistola de silicón Truper Chica|7508623494860|7508623494860|2|23788d63
Pistola de silicón Truper Grande|7508517589931|7508517589931|0|ae6d0ef3
Limpiapipas Surtido|||0|c8414ae5
Palitos de madera Abatelenguas|P000066|P000066|1|7a1e9241
Palitos de madera Paleta|||2|b27f2f67
Palitos de madera Brocheta|P000067|P000067|0|7cce06d5
Bola de unicel 3 cm|P000068|P000068|0|bd75a68b
Bola de unicel 5 cm|P000069|P000069|0|15946c4b
Bola de unicel 7 cm|||0|6db3320b
Bola de unicel 10 cm|||0|a816a761
Placa de unicel 1 cm|||0|c4babd0f
Placa de unicel 2 cm|||1|f0ca1fef
Listón 0.5 cm · Blanco|P000070|P000070|2|270ae7d9
Listón 0.5 cm · Amarillo|P000071|P000071|2|e385c2d9
Listón 0.5 cm · Rosa|||2|3189cfd9
Listón 0.5 cm · Azul|P000072|P000072|0|202002b9
Listón 0.5 cm · Verde|||0|58cbabcb
Listón 0.5 cm · Rojo|P000073|P000073|7|3aa59f79
Listón 0.5 cm · Naranja|||2|d645e909
Listón 0.5 cm · Negro|||5|a1dd8f1d
Listón 0.5 cm · Morado|P000074|P000074|1|9ec4c479
Listón 0.5 cm · Café|P000075|P000075|3|8d3f0d95
Listón 0.5 cm · Gris|P000076|P000076|0|34100599
Listón 0.5 cm · Dorado|P000077|P000077|0|546bb719
Listón 0.5 cm · Plateado|P000078|P000078|3|57e2712f
Listón 0.5 cm · Turquesa|P000079|P000079|1|24749979
Listón 1 cm · Blanco|||3|e66532d9
Listón 1 cm · Amarillo|P000080|P000080|2|86c28dd9
Listón 1 cm · Rosa|P000081|P000081|1|b7c19ad9
Listón 1 cm · Azul|P000082|P000082|3|a657cdb9
Listón 1 cm · Verde|P000083|P000083|1|303cb6cb
Listón 1 cm · Rojo|||0|c0dd6a79
Listón 1 cm · Naranja|P000084|P000084|4|28397409
Listón 1 cm · Negro|P000085|P000085|2|794e9a1d
Listón 1 cm · Morado|P000086|P000086|1|5e1f0f79
Listón 1 cm · Café|P000087|P000087|1|1376d895
Listón 1 cm · Gris|P000088|P000088|2|ba47d099
Listón 1 cm · Dorado|||0|13c60219
Listón 1 cm · Plateado|||0|83d1862f
Listón 1 cm · Turquesa|P000089|P000089|1|c7b16479
Listón 2.5 cm · Blanco|P000090|P000090|0|9bfc7299
Listón 2.5 cm · Amarillo|||3|1b33ed99
Listón 2.5 cm · Rosa|||1|cc6eba99
Listón 2.5 cm · Azul|P000091|P000091|2|bb04ed79
Listón 2.5 cm · Verde|P000092|P000092|0|ce20e68b
Listón 2.5 cm · Rojo|P000093|P000093|1|d58a8a39
Listón 2.5 cm · Naranja|P000094|P000094|1|d4ffc3c9
Listón 2.5 cm · Negro|||5|1732c9dd
Listón 2.5 cm · Morado|P000095|P000095|2|13b64f39
Listón 2.5 cm · Café|||6|2823f855
Listón 2.5 cm · Gris|P000096|P000096|0|cef4f059
Listón 2.5 cm · Dorado|||2|c95d41d9
Listón 2.5 cm · Plateado|||5|81223e6f
Listón 2.5 cm · Turquesa|||1|5c22c439
Cordón de seda Rojo|P000097|P000097|1|de5ab4ae
Cordón de seda Azul|P000098|P000098|0|c3d517ee
Cordón de seda Blanco|P000099|P000099|0|13185a8e
Cordón de seda Dorado|||1|407929ce
Cordón de seda Negro|||0|2ca22b12
Cordón de seda Verde|||0|e39047c0
Estambre Omega Blanco|7505670968078|7505670968078|0|17f33d41
Estambre Omega Amarillo|7505042742787|7505042742787|1|d3f52441
Estambre Omega Rosa|7505180022710|7505180022710|0|8d851941
Estambre Omega Azul|7503604575538|7503604575538|1|7c1b4c21
Estambre Omega Verde|7509615004777|7509615004777|0|a31a3b33
Estambre Omega Rojo|7506723446796|7506723446796|0|96a0e8e1
Estambre Omega Naranja|7508949800772|7508949800772|0|2e6e8471
Estambre Omega Negro|7503514305911|7503514305911|1|ec2c1e85
Estambre Omega Morado|||1|8fad19e1
Estambre Omega Café|7504725570952|7504725570952|1|e93a56fd
Estambre Omega Gris|7503418140366|7503418140366|2|900b4f01
Estambre Omega Dorado|7504928999666|7504928999666|1|45540c81
Estambre Omega Plateado|7505204103883|7505204103883|0|8404c8c7
Estambre Omega Turquesa|7508437796518|7508437796518|0|14e3fae1
Estambre Omega Lila|7506943348603|7506943348603|0|a195e2e1
Estambre Omega Azul cielo|7504040809225|7504040809225|1|df9aa4d6
"""

    private const val PRODUCTOS_3 = """
Ojos móviles Chicos|||1|4b89d84d
Ojos móviles Medianos|||0|aae566b7
Ojos móviles Grandes|||0|d9160bff
Pegamento blanco a granel Resistol|7505039827664|7505039827664|1|8c1398fb
Pegamento blanco Resistol 60 g|||3|8fac5df7
Pegamento blanco Resistol 125 g|||0|2da9d637
Pegamento blanco Resistol 250 g|7500214561746|7500214561746|0|b9d573d7
Pegamento blanco Pritt 60 g|7503549334535|7503549334535|3|6ab8d181
Pegamento blanco Pritt 125 g|7507107075083|7507107075083|1|77678f41
Pegamento blanco Pritt 250 g|7503209502946|7503209502946|7|03932ce1
Lápiz adhesivo Pritt 10 g|||0|94e1595a
Lápiz adhesivo Pritt 20 g|7503872566450|7503872566450|0|cd95f91c
Lápiz adhesivo Pritt 40 g|7505047815769|7505047815769|0|3eff38a0
Lápiz adhesivo Pelikan 10 g|7503490225340|7503490225340|5|d7da5ad8
Lápiz adhesivo Pelikan 20 g|7503270043188|7503270043188|2|108efa9a
Lápiz adhesivo Pelikan 40 g|7508255686459|7508255686459|0|81f83a1e
Lápiz adhesivo Dixon 10 g|7500470300950|7500470300950|1|2a1beb70
Lápiz adhesivo Dixon 20 g|7503834163734|7503834163734|5|62d08b32
Lápiz adhesivo Dixon 40 g|7502676766585|7502676766585|1|d439cab6
Regla Maped 15 cm|7509536213814|7509536213814|6|2eb9c18c
Regla Maped 30 cm|7506623029280|7506623029280|7|9574759a
Regla Maped Flexible 30 cm|7503241233716|7503241233716|10|cc08d95c
Regla Barrilito 15 cm|7507670969864|7507670969864|13|843f861a
Regla Barrilito 30 cm|7504357169906|7504357169906|4|eafa3a28
Regla Barrilito Flexible 30 cm|7500337020397|7500337020397|4|ea2ff23c
Regla Baco 15 cm|7500708761393|7500708761393|13|01e0f89a
Regla Baco 30 cm|7502598312556|7502598312556|2|689baca8
Regla Baco Flexible 30 cm|7509359500238|7509359500238|0|ffe7bf46
Juego de geometría Maped Escolar|7506729454320|7506729454320|5|a3cc0403
Juego de geometría Maped Profesional|7504738848260|7504738848260|9|872c8923
Juego de geometría Barrilito Escolar|7508071852953|7508071852953|4|707639e3
Juego de geometría Barrilito Profesional|7505389435564|7505389435564|1|f0b2df03
Juego de geometría Baco Escolar|7500456555732|7500456555732|1|f89bc1ad
Juego de geometría Baco Profesional|7503681955681|7503681955681|5|9860fccd
Compás Maped Escolar|7509670345457|7509670345457|2|044897c2
Compás Maped De precisión|7506259356743|7506259356743|2|2f015a41
Compás Barrilito Escolar|7508389460710|7508389460710|1|7fbe9ea2
Compás Barrilito De precisión|7508827136627|7508827136627|1|b1350f61
Compás Staedtler Escolar|7508823546925|7508823546925|0|2699dfe2
Compás Staedtler De precisión|7500463300363|7500463300363|0|48cf3621
Transportador Barrilito 180°|7507203793799|7507203793799|1|c39c6bce
Transportador Barrilito 360°|7506748181634|7506748181634|1|83a75146
Transportador Baco 180°|7501835042744|7501835042744|0|d21feb58
Transportador Baco 360°|7508671563075|7508671563075|2|922ad0d0
Tijeras escolares Maped Punta roma|7507085578842|7507085578842|0|71abc1e2
Tijeras escolares Maped Punta fina|7505789208751|7505789208751|1|ec7b7c6c
Tijeras escolares Barrilito Punta roma|7508471187006|7508471187006|2|f65fbd02
Tijeras escolares Barrilito Punta fina|7508114262855|7508114262855|5|712f778c
Tijeras escolares Pelikan Punta roma|7505727504242|7505727504242|2|5af84102
Tijeras escolares Pelikan Punta fina|7502334060857|7502334060857|1|d5c7fb8c
Calculadora Casio Básica|7509720690292|7509720690292|0|cf88ac26
Calculadora Casio Científica|7503241344092|7503241344092|0|c66ddd0e
Calculadora Texas Instruments Básica|7505298972303|7505298972303|1|8106b366
Calculadora Texas Instruments Científica|7509439836783|7509439836783|1|0a10a44e
Mapa Carta · México división política|P000100|P000100|7|f9a2624a
Mapa Carta · México físico|P000101|P000101|6|4e705f90
Mapa Carta · México con nombres|||6|d830e3e0
Mapa Carta · América|||10|e087dbce
Mapa Carta · Mundo|||21|d4c6e850
Mapa Carta · Europa|||3|f38c8b8c
Mapa Carta · Asia|P000102|P000102|5|3650bc64
Mapa Carta · África|||19|748f08c4
Mapa Carta · Oceanía|P000103|P000103|4|0c8a8eb4
Mapa Carta · Mi estado|P000104|P000104|31|73499244
Mapa Doble carta · México división política|P000105|P000105|2|8c5d4af2
Mapa Doble carta · México físico|||0|655413e8
Mapa Doble carta · México con nombres|P000106|P000106|3|71a6c038
Mapa Doble carta · América|P000107|P000107|4|4c316826
Mapa Doble carta · Mundo|P000108|P000108|4|8f1460a8
Mapa Doble carta · Europa|||6|7cb1b234
Mapa Doble carta · Asia|||4|b223770c
Mapa Doble carta · África|P000109|P000109|8|fdb42f6c
Mapa Doble carta · Oceanía|||4|e100ab5c
Mapa Doble carta · Mi estado|P000110|P000110|16|def31e9c
Monografía Benito Juárez|P000111|P000111|0|e3ebd1c2
Monografía Miguel Hidalgo|||4|4572fe50
Monografía José María Morelos|||7|a2cb4b5c
Monografía Josefa Ortiz|||0|e036fc4e
Monografía Emiliano Zapata|P000112|P000112|0|352ed754
Monografía Francisco Villa|P000113|P000113|0|ebcdef52
Monografía Lázaro Cárdenas|P000114|P000114|0|d82f5336
Monografía Sor Juana|P000115|P000115|2|fd012832
Monografía Independencia|P000116|P000116|1|411b85ce
Monografía Revolución|P000117|P000117|2|f029cf3e
Monografía Reforma|P000118|P000118|1|5e4a542e
Monografía Expropiación petrolera|||4|1da38cb0
Monografía Batalla de Puebla|||2|2bb434fb
Monografía Niños Héroes|||3|e4724486
Monografía Conquista|||0|0e900dce
Monografía Culturas prehispánicas|||1|84c68d40
Monografía Aztecas|P000119|P000119|6|2bf2514e
Monografía Mayas|P000120|P000120|3|5af25c0e
Monografía Olmecas|||0|372ce0ee
Monografía Aparato digestivo|||4|8c992bb2
Monografía Aparato respiratorio|P000121|P000121|11|6802b27c
Monografía Aparato circulatorio|P000122|P000122|4|a4c06c26
Monografía Sistema nervioso|||1|e380f14c
Monografía Esqueleto|P000123|P000123|1|17981b8e
Monografía Cuerpo humano|||0|5516407a
Monografía Los sentidos|||5|50b72aac
Monografía Sistema solar|P000124|P000124|6|c5c89692
Monografía Ciclo del agua|||0|a348dccb
Monografía Estados de la materia|||8|0479315e
Monografía La célula|P000125|P000125|0|aff0f861
Monografía Animales vertebrados|||2|e694de1e
Monografía Animales invertebrados|P000126|P000126|2|ea44547e
Monografía Plantas|P000127|P000127|0|9c01f98e
Monografía La flor|P000128|P000128|12|f2740faf
Monografía Ecosistemas|||0|cff5022e
Monografía Cadena alimenticia|P000129|P000129|9|9be7602e
Monografía Símbolos patrios|||1|3f5cf376
Monografía Estados de México|P000130|P000130|4|0c8bd6d1
Monografía Continentes|P000131|P000131|6|cbc8ea2e
Monografía Medios de transporte|||4|67a7c627
Lámina didáctica Abecedario|||3|99499872
Lámina didáctica Números|||0|c64cb9bf
Lámina didáctica Tablas de multiplicar|P000132|P000132|0|2d656959
Lámina didáctica Figuras geométricas|P000133|P000133|3|c8c7ba74
Lámina didáctica Colores|P000134|P000134|1|793622be
Lámina didáctica Frutas|P000135|P000135|1|f7981fc2
Lámina didáctica Verduras|P000136|P000136|0|1038cd68
Lámina didáctica Animales de la granja|P000137|P000137|0|011d0eb2
Lámina didáctica Animales salvajes|P000138|P000138|0|c98efbc2
Lámina didáctica Partes de la planta|||2|5e3e830e
Lámina didáctica El reloj|||0|2d6fd211
Lámina didáctica Días de la semana|P000139|P000139|1|a601bf24
Lámina didáctica Meses del año|P000140|P000140|0|e18352cd
Lámina didáctica Estaciones del año|||0|81161f31
Lámina didáctica Profesiones|||0|0f2ef788
Lámina didáctica Medidas|P000141|P000141|1|d628e348
Lámina didáctica Fracciones|||1|f751beca
Lámina didáctica Signos de puntuación|P000142|P000142|0|d20a239d
Lámina didáctica Verbos|P000143|P000143|0|e03ccc8e
Lámina didáctica Sustantivos|P000144|P000144|0|e62ff948
Lámina didáctica Planetas|P000145|P000145|2|d7064c1c
Lámina didáctica Dinosaurios|P000146|P000146|0|237436ee
Lámina didáctica Higiene|P000147|P000147|2|b51fa348
Lámina didáctica Alimentación saludable|||0|828417e8
Lámina didáctica Reciclaje|P000148|P000148|1|5a9b82e8
Lámina didáctica Regiones de México|P000149|P000149|1|9793a453
Lámina didáctica Ríos de México|P000150|P000150|1|1e5fd6dd
Lámina didáctica Volcanes|P000151|P000151|2|59a5965e
Lámina didáctica Banderas del mundo|||4|cab7fb9b
Lámina didáctica Mapa de México|P000152|P000152|8|5c299c8d
Estampas Corazones|||2|a438c260
Estampas Estrellas|P000153|P000153|0|cff37100
Estampas Caritas|||3|76279500
Estampas Animales|P000154|P000154|3|c73f2494
Estampas Dinosaurios|P000155|P000155|0|04d90b36
Estampas Princesas|||0|e0765c60
Estampas Superhéroes|P000156|P000156|1|49a76ebc
Estampas Futbol|||0|8b8fd7f4
Estampas Flores|||4|d3474dee
Estampas Mariposas|P000157|P000157|0|ff94b300
Estampas Unicornios|P000158|P000158|0|58608f2a
Estampas Autos|P000159|P000159|2|eb38d960
Estampas Espacio|P000160|P000160|1|2e7c7920
Estampas Frutas|P000161|P000161|1|f17e447a
Estampas Emojis|P000162|P000162|0|0845da9a
Estampas Letras|||2|f8140512
Estampas Números|||0|51ce5607
Estampas Calaveras|||3|a21ad8a0
Estampas Navidad|||1|0a9a31c0
Estampas Halloween|P000163|P000163|1|d9126fc0
Estampas Día de muertos|P000164|P000164|1|50ef0343
Estampas Amor y amistad|||4|53ee0a89
Estampas Felicitaciones|||1|695fb744
Estampas Escolares|||0|9c096940
Estampas Premios|P000165|P000165|4|bfc84d40
Estampas Brillantes|||2|3043c1f8
Estampas Fosforescentes|P000166|P000166|1|eb6df646
Estampas Holográficas|P000167|P000167|1|789cf68c
Estampas De fieltro|P000168|P000168|4|504d3a1f
Estampas De foamy|P000169|P000169|1|994b4d3f
Estampas Mascotas|P000170|P000170|3|216fbf76
Estampas Comida|||3|47bae3ca
Estampas Música|||0|e6d1787d
Estampas Deportes|P000171|P000171|0|f923df20
Estampas Selva|||2|461fea40
Estampas Océano|||0|7d4c581e
Estampas Granja|||2|63578582
Estampas Profesiones|||3|8f189640
Estampas Banderas|P000172|P000172|1|72518b68
Estampas Paisajes|||0|d81eb324
Libro para colorear Animales|P000173|P000173|0|93f20cce
Libro para colorear Dinosaurios|||0|71049abc
Libro para colorear Princesas|||1|4c101e1a
Libro para colorear Autos|P000174|P000174|0|5164f51a
Libro para colorear Mandalas|P000175|P000175|0|096ae308
Libro para colorear Superhéroes|P000176|P000176|0|eab5c376
Libro para colorear Granja|P000177|P000177|1|2d01a488
Libro para colorear Océano|P000178|P000178|0|08e06d58
Libro para colorear Espacio|P000179|P000179|2|256027da
Libro para colorear Unicornios|P000180|P000180|0|4490ca64
Libro para colorear Navidad|||0|1238a746
Libro para colorear Abecedario|||1|6a2bc064
Libro para colorear Números|P000181|P000181|0|596ccb8d
Libro para colorear Vehículos|||0|f97bf35e
Libro para colorear Mascotas|P000182|P000182|0|ee22a7b0
Etiquetas escolares Niño|P000183|P000183|4|8d7a57d2
Etiquetas escolares Niña|||5|a8e31a36
Etiquetas escolares Neutras|P000184|P000184|2|67afc140
Pizarrón blanco 30 × 40 cm|P000185|P000185|0|fe7b43a3
Pizarrón blanco 60 × 90 cm|P000186|P000186|0|09348673
Gises Vinci Blanco|7505821728131|7505821728131|0|7a83ac3b
Gises Vinci Colores|7505061040611|7505061040611|0|93c69c46
Clips Acco No. 1|7501946809823|7501946809823 7508581593100|0|664dcfda
Clips Acco No. 2|7505876702162|7505876702162|0|8f76f19c
Clips Acco Mariposa|7502112544159|7502112544159 7508224681508|0|08a3f354
Clips Acco Jumbo|7505462932447|7505462932447|1|b3a7d8d8
Grapas Swingline Estándar|7506793400681|7506793400681|2|4d7a04dc
Grapas Swingline Uso rudo|7505809032618|7505809032618|0|021b12a4
Grapas Pegaso Estándar|7509258370406|7509258370406|1|aa16763c
Grapas Pegaso Uso rudo|7509948160799|7509948160799|1|bce6061a
Engrapadora Swingline Mini|7509255068153|7509255068153|0|31e86232
Engrapadora Swingline Estándar|7503455838622|7503455838622|0|340691e4
Engrapadora Swingline Industrial|7503924747523|7503924747523|0|959ad7b2
Engrapadora Pegaso Mini|7506281277665|7506281277665|1|9a8516d2
Engrapadora Pegaso Estándar|7500300683376|7500300683376|0|64336944
Engrapadora Pegaso Industrial|7502038575664|7502038575664|0|26918e7c
Engrapadora Acco Mini|7508997411609|7508997411609|0|e25a18f2
Engrapadora Acco Estándar|7506162408737|7506162408737|0|513dc324
Engrapadora Acco Industrial|7501903733321|7501903733321|0|d6887b76
Perforadora Acco 1 orificio|7504438939008|7504438939008|2|a7154e9c
Perforadora Acco 2 orificios|7501250826042|7501250826042|0|47b71d7c
Perforadora Acco 3 orificios|||0|526d251c
Perforadora Pegaso 1 orificio|7502580589720|7502580589720|0|9e924c56
Perforadora Pegaso 2 orificios|7509486087817|7509486087817|0|53eb94b6
Perforadora Pegaso 3 orificios|7506756159694|7506756159694|0|5ea19c56
Perforadora Maped 1 orificio|7501947102664|7501947102664|1|1776a4c0
Perforadora Maped 2 orificios|7502605526082|7502605526082|0|251b7aa0
Perforadora Maped 3 orificios|7500020091864|7500020091864|1|2fd18240
Notas adhesivas Post-it Amarillo · 3 × 3|7503868782376|7503868782376|2|384be31b
Notas adhesivas Post-it Amarillo · 1.5 × 2|7503715076344|7503715076344|2|fba9cbf5
Notas adhesivas Post-it Neón surtido · 3 × 3|7502701089986|7502701089986|0|14c3eb0d
Notas adhesivas Post-it Neón surtido · 1.5 × 2|7501817912614|7501817912614|3|f12b78e7
Notas adhesivas Pelikan Amarillo · 3 × 3|7507442690453|7507442690453|2|ecf126bb
Notas adhesivas Pelikan Amarillo · 1.5 × 2|7506049179422|7506049179422|2|3d669f95
Notas adhesivas Pelikan Neón surtido · 3 × 3|||1|b040bb6d
Notas adhesivas Pelikan Neón surtido · 1.5 × 2|7501263091420|7501263091420|1|1912b947
Banderitas adhesivas Post-it Neón|7503399360555|7503399360555|0|322ec78a
Banderitas adhesivas Post-it Pastel|7500000223605|7500000223605|0|d2285bd4
Banderitas adhesivas Pelikan Neón|7507374788778|7507374788778|0|ce3c0fea
Banderitas adhesivas Pelikan Pastel|7509060961908|7509060961908|1|78b10cb6
Cinta adhesiva transparente Scotch 12 mm|7509474861320|7509474861320|0|43e19bfd
Cinta adhesiva transparente Scotch 18 mm|7502790899381|7502790899381|3|771daef1
Cinta adhesiva transparente Scotch 24 mm|7509749705335|7509749705335|0|ddac043b
Cinta adhesiva transparente Janel 12 mm|7502967884875|7502967884875|0|3a1a0d95
Cinta adhesiva transparente Janel 18 mm|7501303067545|7501303067545|4|6d562089
Cinta adhesiva transparente Janel 24 mm|7507246528990|7507246528990|0|d3e475d3
Cinta adhesiva transparente Tuk 12 mm|7501417931923|7501417931923|1|16cdcc2d
Cinta adhesiva transparente Tuk 18 mm|7502258816370|7502258816370|1|4a09df21
Cinta adhesiva transparente Tuk 24 mm|7500943400866|7500943400866|1|b098346b
Cinta de empaque Scotch Canela 48 mm|7506769128878|7506769128878|4|10347f17
Cinta de empaque Scotch Masking 24 mm|7504139266410|7504139266410|1|d756d227
Cinta de empaque Scotch Masking 48 mm|7500627862645|7500627862645|2|0aeba2a3
Cinta de empaque Scotch Doble cara|7500235886330|7500235886330|2|7718be2d
Cinta de empaque Janel Canela 48 mm|7509199416737|7509199416737|2|c138eeab
Cinta de empaque Janel Masking 24 mm|7506512314664|7506512314664|5|58fdacbb
Cinta de empaque Janel Masking 48 mm|7502112519560|7502112519560|2|8c927d37
Cinta de empaque Janel Doble cara|7507292884774|7507292884774|1|ec38ebc1
Cinta de empaque Tuk Canela 48 mm|7507315634720|7507315634720|2|0a33122b
Cinta de empaque Tuk Masking 24 mm|7506537904550|7506537904550|0|4bfcf03b
Cinta de empaque Tuk Masking 48 mm|7502386604290|7502386604290|0|7f91c0b7
Cinta de empaque Tuk Doble cara|7501714771642|7501714771642|0|5cac4f41
Sobre Manila carta|P000187|P000187|0|1233a352
Sobre Manila oficio|P000188|P000188|3|7f27394c
Sobre Bolsa|||5|8c3b91a0
Sobre Blanco carta|||3|660f2fff
Sobre Coin|P000189|P000189|4|7496ee82
Folder Oxford Carta · Crema|7504663888973|7504663888973 7506440729448|5|2ecaad2b
Folder Oxford Carta · Azul|7504296007215|7504296007215 7502085854422|9|3c8ded0d
Folder Oxford Carta · Rojo|7508478828513|7508478828513|8|571389cd
Folder Oxford Carta · Verde|||2|1168511f
Folder Oxford Carta · Amarillo|7506646637059|7506646637059 7509984469900|6|76de192d
Folder Oxford Carta · Rosa|7500839183897|7500839183897 7506112955724|3|4df7ba2d
Folder Oxford Carta · Naranja|7506707110477|7506707110477 7500510809856|4|faa7c45d
Folder Oxford Carta · Morado|7506142000685|7506142000685|14|a2cd64cd
Folder Oxford Oficio · Crema|7500413490854|7500413490854|16|02ed5cf1
Folder Oxford Oficio · Azul|7504979037102|7504979037102 7507053592542|2|cf05d653
Folder Oxford Oficio · Rojo|7502376067159|7502376067159 7508998270946|3|e98b7313
Folder Oxford Oficio · Verde|7507899634277|7507899634277 7504844945921|4|e58b00e5
Folder Oxford Oficio · Amarillo|||12|76135c73
Folder Oxford Oficio · Rosa|7502032751835|7502032751835 7505238809515|4|e06fa373
Folder Oxford Oficio · Naranja|7508653580861|7508653580861|18|9ac12123
Folder Oxford Oficio · Morado|7507359389037|7507359389037|1|f9033b13
Folder Mapasa Carta · Crema|||6|00592b89
Folder Mapasa Carta · Azul|7500077967648|7500077967648|2|81c46aeb
Folder Mapasa Carta · Rojo|7507577938154|7507577938154 7501633297261|0|9c4a07ab
Folder Mapasa Carta · Verde|7504755097405|7504755097405|2|e2f6cf7d
Folder Mapasa Carta · Amarillo|7504250180008|7504250180008 7503153912815|0|239fd90b
Folder Mapasa Carta · Rosa|7507337618418|7507337618418 7500472598768|3|932e380b
Folder Mapasa Carta · Naranja|7504007522792|7504007522792|9|49f3e3bb
Folder Mapasa Carta · Morado|7508553440906|7508553440906 7503051616013|2|4ab5c3ab
Folder Mapasa Oficio · Crema|7504394419118|7504394419118 7500132172970|8|aad5bbcf
Folder Mapasa Oficio · Azul|7500229941557|7500229941557 7503352811568|3|a09454b1
Folder Mapasa Oficio · Rojo|7504173466128|7504173466128|7|bb19f171
Folder Mapasa Oficio · Verde|7503581686432|7503581686432|2|8d735fc3
Folder Mapasa Oficio · Amarillo|7500598557236|7500598557236|0|f9649cd1
Folder Mapasa Oficio · Rosa|7509889305730|7509889305730 7506506921724|2|b1fe21d1
Folder Mapasa Oficio · Naranja|7507359917377|7507359917377|0|4782e101
Folder Mapasa Oficio · Morado|7508829695788|7508829695788|5|484f5a71
Carpeta de argollas Avery 1\u0022 · Blanca|7509525441105|7509525441105|1|394bd092
Carpeta de argollas Avery 1\u0022 · Negra|7505878788904|7505878788904|0|a710023a
Carpeta de argollas Avery 1\u0022 · Azul|7501041529060|7501041529060|1|ef205fa4
Carpeta de argollas Avery 1\u0022 · Roja|7507181760042|7507181760042|2|9deef132
Carpeta de argollas Avery 1\u0022 · Verde|7506628152563|7506628152563|1|692879f6
Carpeta de argollas Avery 1.5\u0022 · Blanca|7506627913059|7506627913059|1|6914d7a0
Carpeta de argollas Avery 1.5\u0022 · Negra|7504512365815|7504512365815|0|9feb74c8
Carpeta de argollas Avery 1.5\u0022 · Azul|7503738475445|7503738475445|0|41b31db2
Carpeta de argollas Avery 1.5\u0022 · Roja|7503989278710|7503989278710|1|f081af40
Carpeta de argollas Avery 1.5\u0022 · Verde|7500802651514|7500802651514|1|6203ec84
Carpeta de argollas Avery 2\u0022 · Blanca|7509620029178|7509620029178|0|8785a994
Carpeta de argollas Avery 2\u0022 · Negra|7505094780881|7505094780881|0|549033bc
Carpeta de argollas Avery 2\u0022 · Azul|7505238493899|7505238493899|1|72a309a6
Carpeta de argollas Avery 2\u0022 · Roja|7502420337603|7502420337603|0|21719b34
Carpeta de argollas Avery 2\u0022 · Verde|7504892310788|7504892310788|0|16a8ab78
Carpeta de argollas Avery 3\u0022 · Blanca|7506012462575|7506012462575|0|d5bf8296
Carpeta de argollas Avery 3\u0022 · Negra|||0|0210653e
Carpeta de argollas Avery 3\u0022 · Azul|7501114148716|7501114148716|0|f625b3a8
Carpeta de argollas Avery 3\u0022 · Roja|7501354919770|7501354919770|4|a4f44536
Carpeta de argollas Avery 3\u0022 · Verde|7501597493266|7501597493266|1|c428dcfa
Protector de hojas Avery Carta|7500239082356|7500239082356 7500022635950|2|0a1c3daf
Protector de hojas Avery Oficio|7506353048698|7506353048698|0|1f52b069
Separadores Avery 5 divisiones|7507177623443|7507177623443|1|82d0daf6
Separadores Avery 10 divisiones|7502179159068|7502179159068|0|dff19498
Separadores Avery A-Z|7507084592474|7507084592474|1|a636fba0
Broche para archivo Baco 8 cm|7500470674068|7500470674068 7505017806193|0|dc3a4d70
Broche para archivo Acco 8 cm|7504750661908|7504750661908|0|57afa990
Ligas a granel No. 18|||0|e6a1c0f4
Ligas a granel No. 33|||1|a06c4a54
Ligas a granel Surtidas|||0|b391eda8
Cojín para sello Pelikan Negro|7505853492222|7505853492222|0|6c2f0ded
Cojín para sello Pelikan Azul|7507361871896|7507361871896|0|af62f589
Cojín para sello Pelikan Rojo|7506448221128|7506448221128|0|c9e89249
Lapicera Chenson Sencilla · Rojo|7506283668096|7506283668096|0|83d8587f
Lapicera Chenson Sencilla · Azul|7504258118089|7504258118089|0|6952bbbf
Lapicera Chenson Sencilla · Negro|7504837633828|7504837633828|1|69461ea3
Lapicera Chenson Sencilla · Rosa|7503121427143|7503121427143|2|7abc88df
Lapicera Chenson Sencilla · Morado|7507184232225|7507184232225|0|2e478a7f
Lapicera Chenson Sencilla · Verde|7509603540317|7509603540317|1|20343b51
Lapicera Chenson Sencilla · Gris|7505277722875|7505277722875|3|7d42be9f
Lapicera Chenson Sencilla · Estampada|7500569136811|7500569136811|1|a7ec9e62
Lapicera Chenson Doble cierre · Rojo|7500823732070|7500823732070|3|f676274d
Lapicera Chenson Doble cierre · Azul|7508386795631|7508386795631|0|dbf08a8d
Lapicera Chenson Doble cierre · Negro|7504310437844|7504310437844|0|abab71f1
Lapicera Chenson Doble cierre · Rosa|7506085735989|7506085735989|1|ed5a57ad
Lapicera Chenson Doble cierre · Morado|7500941160014|7500941160014|1|6ca5424d
Lapicera Chenson Doble cierre · Verde|7506446722023|7506446722023|0|62998e9f
Lapicera Chenson Doble cierre · Gris|7503551070520|7503551070520|1|efe08d6d
Lapicera Chenson Doble cierre · Estampada|7504613763664|7504613763664|3|1228c3b0
Lapicera Chenson Cartuchera · Rojo|7502536820099|7502536820099|4|e632dc09
Lapicera Chenson Cartuchera · Azul|7505960467885|7505960467885|2|cbad3f49
Lapicera Chenson Cartuchera · Negro|7507383877517|7507383877517|0|9f0de7ad
Lapicera Chenson Cartuchera · Rosa|||4|dd170c69
Lapicera Chenson Cartuchera · Morado|7509387945919|7509387945919|1|11413909
Lapicera Chenson Cartuchera · Verde|7503048283655|7503048283655|0|55fc045b
Lapicera Chenson Cartuchera · Gris|||3|df9d4229
Lapicera Chenson Cartuchera · Estampada|7502097719658|7502097719658|1|ac87bd6c
Mochila Chenson Primaria · Azul|7507520028895|7507520028895|1|d77daa1d
Mochila Chenson Primaria · Negro|7507772525524|7507772525524|0|f86eed81
Mochila Chenson Primaria · Rosa|7504019089719|7504019089719|1|e8e7773d
Mochila Chenson Primaria · Morado|7505459842872|7505459842872|0|967819dd
Mochila Chenson Primaria · Gris|7501554099111|7501554099111|0|eb6dacfd
Mochila Chenson Primaria · Estampada|7503914411533|7503914411533|1|33bfaf40
Mochila Chenson Secundaria · Azul|7508640691655|7508640691655|1|60ab8835
Mochila Chenson Secundaria · Negro|||0|ed9da599
Mochila Chenson Secundaria · Rosa|7506504170698|7506504170698|1|72155555
Mochila Chenson Secundaria · Morado|7509968043911|7509968043911|0|fad92bf5
Mochila Chenson Secundaria · Gris|7504749415215|7504749415215|0|749b8b15
Mochila Chenson Secundaria · Estampada|7509894767059|7509894767059|1|3120cf58
Lonchera Azul|||0|77373043
Lonchera Rosa|P000190|P000190|0|88a0fd63
Lonchera Verde|P000191|P000191|0|46cd9ed5
Bolsa de regalo Chica · Cumpleaños|||0|e21f6659
Bolsa de regalo Chica · Bebé|P000192|P000192|1|1d412779
Bolsa de regalo Chica · Navidad|P000193|P000193|1|2b89a0f7
Bolsa de regalo Chica · Amor|P000194|P000194|1|78a3e6b9
Bolsa de regalo Chica · Lisa|P000195|P000195|1|c4da6bbf
Bolsa de regalo Chica · Graduación|||0|4b839f7d
Bolsa de regalo Chica · Infantil|||3|e02f42bb
Bolsa de regalo Chica · Kraft|P000196|P000196|0|2b06e9c5
Bolsa de regalo Mediana · Cumpleaños|P000197|P000197|0|f6a6b979
Bolsa de regalo Mediana · Bebé|P000198|P000198|0|8a60b059
Bolsa de regalo Mediana · Navidad|||2|c055cc17
Bolsa de regalo Mediana · Amor|||2|f836e9d9
Bolsa de regalo Mediana · Lisa|P000199|P000199|1|31f9f49f
Bolsa de regalo Mediana · Graduación|||2|600af29d
Bolsa de regalo Mediana · Infantil|||0|d527eb9b
Bolsa de regalo Mediana · Kraft|P000200|P000200|3|11e5a4e5
Bolsa de regalo Grande · Cumpleaños|P000201|P000201|1|e1a02049
Bolsa de regalo Grande · Bebé|||1|f5fb8f89
Bolsa de regalo Grande · Navidad|P000202|P000202|2|20d88ee7
Bolsa de regalo Grande · Amor|||0|977808a9
Bolsa de regalo Grande · Lisa|P000203|P000203|0|9d94d3cf
Bolsa de regalo Grande · Graduación|P000204|P000204|1|4b04596d
Bolsa de regalo Grande · Infantil|P000205|P000205|0|70489acb
Bolsa de regalo Grande · Kraft|P000206|P000206|0|e55a4fb5
Tarjeta de felicitación Cumpleaños|||0|b9dc6b85
Tarjeta de felicitación Amor|P000207|P000207|0|b13e4de5
Tarjeta de felicitación Día de las madres|P000208|P000208|1|7c72ecc2
Tarjeta de felicitación Día del padre|P000209|P000209|1|fdbe61f4
Tarjeta de felicitación Navidad|P000210|P000210|1|c20cf723
Tarjeta de felicitación Graduación|P000211|P000211|2|2340a4a9
Tarjeta de felicitación Bebé|P000212|P000212|0|95f5f4cd
Tarjeta de felicitación Boda|P000213|P000213|0|16154fd1
Tarjeta de felicitación Pésame|||0|12e620f7
Tarjeta de felicitación Gracias|P000214|P000214|0|af657a5d
Tarjeta de felicitación Aniversario|||1|af69ea7d
Tarjeta de felicitación Maestro|P000215|P000215|3|f835337d
Moño adherible Rojo|||0|791f935b
Moño adherible Azul|||0|5e99f69b
Moño adherible Dorado|P000216|P000216|1|1b24e9fb
Moño adherible Plateado|||2|684f9a8d
Moño adherible Rosa|||0|7003c3bb
Moño adherible Verde|P000217|P000217|4|e0982f2d
Globo Látex 9\u0022 · Blanco|P000218|P000218|0|e2133669
Globo Látex 9\u0022 · Amarillo|P000219|P000219|1|2da44969
Globo Látex 9\u0022 · Rosa|||1|a99fe669
Globo Látex 9\u0022 · Azul|P000220|P000220|1|98361949
Globo Látex 9\u0022 · Verde|||1|23b65e5b
Globo Látex 9\u0022 · Rojo|||2|b2bbb609
Globo Látex 9\u0022 · Naranja|P000221|P000221|2|f074d399
Globo Látex 9\u0022 · Negro|P000222|P000222|0|6cc841ad
Globo Látex 9\u0022 · Morado|||0|59cd1309
Globo Látex 9\u0022 · Café|P000223|P000223|0|05552425
Globo Látex 12\u0022 · Blanco|||2|513076db
Globo Látex 12\u0022 · Amarillo|P000224|P000224|0|526800db
Globo Látex 12\u0022 · Rosa|P000225|P000225|0|97432fdb
Globo Látex 12\u0022 · Azul|P000226|P000226|0|85d962bb
Globo Látex 12\u0022 · Verde|||1|35ad134d
Globo Látex 12\u0022 · Rojo|P000227|P000227|0|a05eff7b
Globo Látex 12\u0022 · Naranja|P000228|P000228|2|0d43bf8b
Globo Látex 12\u0022 · Negro|P000229|P000229|2|7ebef69f
Globo Látex 12\u0022 · Morado|P000230|P000230|0|c8ea537b
Globo Látex 12\u0022 · Café|P000231|P000231|0|f2f86d97
Globo Metálico · Blanco|P000232|P000232|0|45d6b5bf
Globo Metálico · Amarillo|P000233|P000233|0|fa4c2dbf
Globo Metálico · Rosa|P000234|P000234|0|a08480bf
Globo Metálico · Azul|P000235|P000235|2|8f1ab39f
Globo Metálico · Verde|P000236|P000236|1|f3ddbb31
Globo Metálico · Rojo|||0|a9a0505f
Globo Metálico · Naranja|||0|7155d56f
Globo Metálico · Negro|||1|3cef9e83
Globo Metálico · Morado|P000237|P000237|1|bd90925f
Globo Metálico · Café|||5|fc39be7b
Vela de número 0 · Dorada|||1|11e9356d
Vela de número 0 · Plateada|||0|28ea31db
Vela de número 0 · Rosa|||1|ac8fc7df
Vela de número 0 · Azul|||1|9b25fabf
Vela de número 1 · Dorada|P000238|P000238|0|3d126d0d
Vela de número 1 · Plateada|P000239|P000239|1|62e7ee3b
Vela de número 1 · Rosa|P000240|P000240|1|b745cf7f
Vela de número 1 · Azul|||0|a5dc025f
Vela de número 2 · Dorada|||2|683ba4ad
Vela de número 2 · Plateada|||0|9ce5aa9b
Vela de número 2 · Rosa|||1|c1fbd71f
Vela de número 2 · Azul|P000241|P000241|0|b09209ff
Vela de número 3 · Dorada|P000242|P000242|1|9364dc4d
Vela de número 3 · Plateada|P000243|P000243|3|d6e366fb
Vela de número 3 · Rosa|||1|ccb1debf
Vela de número 3 · Azul|P000244|P000244|1|bb48119f
Vela de número 4 · Dorada|P000245|P000245|1|be8e13ed
Vela de número 4 · Plateada|P000246|P000246|0|10e1235b
Vela de número 4 · Rosa|P000247|P000247|0|d767e65f
Vela de número 4 · Azul|||0|c5fe193f
Vela de número 5 · Dorada|P000248|P000248|0|e9b74b8d
Vela de número 5 · Plateada|P000249|P000249|5|4adedfbb
Vela de número 5 · Rosa|||0|e21dedff
Vela de número 5 · Azul|||0|d0b420df
Vela de número 6 · Dorada|P000250|P000250|0|14e0832d
Vela de número 6 · Plateada|P000251|P000251|1|84dc9c1b
Vela de número 6 · Rosa|P000252|P000252|1|ecd3f59f
Vela de número 6 · Azul|P000253|P000253|1|db6a287f
Vela de número 7 · Dorada|P000254|P000254|2|4009bacd
Vela de número 7 · Plateada|||2|beda587b
Vela de número 7 · Rosa|P000255|P000255|1|f789fd3f
Vela de número 7 · Azul|||0|e620301f
Vela de número 8 · Dorada|P000256|P000256|0|6b32f26d
Vela de número 8 · Plateada|||0|f8d814db
Vela de número 8 · Rosa|||0|024004df
Vela de número 8 · Azul|P000257|P000257|1|f0d637bf
Vela de número 9 · Dorada|P000258|P000258|0|965c2a0d
Vela de número 9 · Plateada|P000259|P000259|0|32d5d13b
Vela de número 9 · Rosa|||1|0cf60c7f
Vela de número 9 · Azul|P000260|P000260|0|fb8c3f5f
Papel celofán Transparente|P000261|P000261|2|205efd9b
Papel celofán Rojo|||0|6f5eba3b
Papel celofán Azul|||0|54d91d7b
Papel celofán Amarillo|P000262|P000262|0|9e60fb9b
Pila Duracell AA|7504588271102|7504588271102|0|80cef538
Pila Duracell AAA|7505219521559|7505219521559|0|a7959bd8
Pila Duracell 9 V|7509599982894|7509599982894|1|b33b70e8
Pila Duracell CR2032|7504381886664|7504381886664|0|39effd10
Pila Energizer AA|7503691521784|7503691521784|0|dd8c633c
Pila Energizer AAA|7509619613265|7509619613265|1|caaf98dc
Pila Energizer 9 V|7500784683664|7500784683664|1|f3611464
Pila Energizer CR2032|7508228884882|7508228884882|3|42712714
Memoria USB Kingston 16 GB|7502492935172|7502492935172|0|2e0e9f9a
Memoria USB Kingston 32 GB|7506495844585|7506495844585|0|755241a6
Memoria USB Kingston 64 GB|7507639399909|7507639399909|0|dcce46a8
Memoria USB Adata 16 GB|7509982654193|7509982654193|0|4345266c
Memoria USB Adata 32 GB|7505281388197|7505281388197|0|8a88c878
Memoria USB Adata 64 GB|7504921786720|7504921786720|0|f204cd7a
Cartucho de tinta HP Modelo A · Negro|7506838050437|7506838050437|1|ecb035a4
Cartucho de tinta HP Modelo A · Color|7504245394434|7504245394434|0|b86617e9
Cartucho de tinta HP Modelo B · Negro|7506595318719|7506595318719|0|2202d544
Cartucho de tinta HP Modelo B · Color|7505572545834|7505572545834|1|edb8b789
Cartucho de tinta HP Modelo C · Negro|7501923279458|7501923279458|0|575574e4
"""

    private const val PRODUCTOS_4 = """
Cartucho de tinta HP Modelo C · Color|7508318561624|7508318561624|0|230b5729
Cartucho de tinta Epson Modelo A · Negro|7502253180582|7502253180582|1|78867894
Cartucho de tinta Epson Modelo A · Color|7502062408945|7502062408945|0|443c5ad9
Cartucho de tinta Epson Modelo B · Negro|7504563678179|7504563678179|0|add91834
Cartucho de tinta Epson Modelo B · Color|7507264887741|7507264887741|0|798efa79
Cartucho de tinta Epson Modelo C · Negro|||0|e32bb7d4
Cartucho de tinta Epson Modelo C · Color|7505021097006|7505021097006|0|aee19a19
Cartucho de tinta Canon Modelo A · Negro|7501615852433|7501615852433|0|e708a594
Cartucho de tinta Canon Modelo A · Color|7507371466150|7507371466150|0|b2be87d9
Cartucho de tinta Canon Modelo B · Negro|7507334818132|7507334818132|1|1c5b4534
Cartucho de tinta Canon Modelo B · Color|7501967769328|7501967769328|0|e8112779
Cartucho de tinta Canon Modelo C · Negro|7502998351162|7502998351162|1|51ade4d4
Cartucho de tinta Canon Modelo C · Color|||0|1d63c719
Cable USB Steren Tipo C · 1 m|7501811756993|7501811756993|1|14661760
Cable USB Steren Tipo C · 2 m|7505211275245|7505211275245|0|28b0b240
Cable USB Steren Micro USB · 1 m|7502329388898|7502329388898|1|e32328b8
Cable USB Steren Micro USB · 2 m|7506812343401|7506812343401|1|f76dc398
Cable USB Steren Lightning · 1 m|||0|08dbb194
Cable USB Steren Lightning · 2 m|7506616676040|7506616676040|0|1d264c74
Cable USB Belkin Tipo C · 1 m|7504626141558|7504626141558|0|566dc800
Cable USB Belkin Tipo C · 2 m|7504257518392|7504257518392|1|6ab862e0
Cable USB Belkin Micro USB · 1 m|7502729267045|7502729267045|1|9c8be158
Cable USB Belkin Micro USB · 2 m|7508246134778|7508246134778|1|b0d67c38
Cable USB Belkin Lightning · 1 m|7504817485584|7504817485584|0|592964f4
Cable USB Belkin Lightning · 2 m|7505962179533|7505962179533|0|6d73ffd4
Audífonos Steren Alámbricos · Negro|7500561327880|7500561327880|1|5f9751ff
Audífonos Steren Alámbricos · Blanco|7508186168390|7508186168390|0|5d5f7a3b
Audífonos Steren Inalámbricos · Negro|7500975308499|7500975308499|0|2858e9f5
Audífonos Steren Inalámbricos · Blanco|7508364825794|7508364825794|0|fc1aecb1
Audífonos Sony Alámbricos · Negro|7500399563337|7500399563337|0|e2fc6c1f
Audífonos Sony Alámbricos · Blanco|7504714480200|7504714480200|0|9be68c5b
Audífonos Sony Inalámbricos · Negro|7509278272575|7509278272575|0|e163f415
Audífonos Sony Inalámbricos · Blanco|7508686242842|7508686242842|0|9e8beed1
Disco Verbatim CD|7502207532863|7502207532863|0|fb149a96
Disco Verbatim DVD|7507275485585|7507275485585|0|cc365cd4
Impresión blanco y negro Carta|||3|77404b63
Impresión blanco y negro Oficio|||5|d3aa211d
Impresión a color Carta|||2|f9101ffa
Impresión a color Oficio|||4|20d72bf4
Copia blanco y negro Carta|||7|214eee67
Copia blanco y negro Oficio|||9|349c1321
Copia blanco y negro Doble cara|||6|accb670f
Copia a color Carta|||2|46267376
Engargolado Hasta 50 hojas|||0|400b4c27
Engargolado 51 a 100 hojas|||3|797d2b71
Engargolado 101 a 200 hojas|||1|12714cc9
Enmicado Credencial|||5|94859054
Enmicado Carta|||2|2ac07ebe
Enmicado Oficio|||7|a7eae9b8
Escaneo|||0|f91a4ba0
Trámite CURP|||5|09b7eec0
Trámite Acta de nacimiento|||3|15ffd1af
Trámite Constancia de situación fiscal|||1|f4f4d663
Trámite Recibo de luz|||3|e35cd2eb
Recarga Telcel \u002420|||1|b86ac202
Recarga Telcel \u002450|||0|571d7422
Recarga Telcel \u0024100|||0|e9f8c4dc
Recarga Telcel \u0024200|||5|7e3d379e
Recarga AT&T \u002420|||1|ba9bc83e
Recarga AT&T \u002450|||0|594e7a5e
Recarga AT&T \u0024100|||1|24012c18
Recarga AT&T \u0024200|||0|b8459eda
Recarga Movistar \u002420|||0|7f0d7f12
Recarga Movistar \u002450|||2|1dc03132
Recarga Movistar \u0024100|||1|92e07dec
Recarga Movistar \u0024200|||0|2724f0ae
Colores Pelikan Caja 36|||0|9069173c
Minas Pentel 0.5 mm 2B|P000263|P000263|1|e21be530
Crayones Pelikan Caja 24|P000264|P000264|0|b84ebe52
CUADERNO PROFESIONAL RAYA JEAN BOOK 100 HOJAS · DISEÑO INFANTIL|P000265|P000265|2|18afbb2b
COLORES PELIKAN CAJA 12|||2|998e4fd8
Plumones de colores Pelikan Caja 10|||3|0a7a644f
CRAYONES BACO CAJA 24|P000266|P000266|1|a14d01a8
Colores Maped Caja 24|P000267|P000267|2|ed38885a
PLUMONES DE COLORES MAPED CAJA 10|||2|34e112cf
Pintura acrílica 20 ml Vinci Gris|||1|8945becb
Pila Duracell 9 V|P000268|P000268|0|b33b70e8
CUADERNO FORMA ITALIANA SCRIBE RAYA · LISA|||2|d2958476
MARCATEXTOS ZEBRA NARANJA|P000269|P000269|0|36c2f275
Pintura textil Vinci Amarillo|P000270|P000270|0|2583c55b
Compás Maped Escolar|P000271|P000271|1|044897c2
"""

    /** Las marcas de los productos, como las manda el POS. */
    const val MARCAS = "AT&T|Acco|Adata|Avery|Azor|Baco|Barrilito|Belkin|Bic|Canon|Casio|Chamex|Chenson|Con-Tact|Copamex|Crayola|Dixon|Duracell|Energizer|Epson|Estrella|Expo|Factis|HP|Janel|Jean Book|Kingston|Kores|Mapasa|Maped|Mapita|Mirado|Movistar|Norma|Omega|Oxford|Paper Mate|Pegaso|Pelikan|Pentel|Pilot|Plastifilm|Politec|Post-it|Prismacolor|Pritt|Resistol|Scotch|Scribe|Sharpie|Sony|Stabilo|Staedtler|Steren|Swingline|Telcel|Texas Instruments|Truper|Tuk|Verbatim|Vinci|Zebra"

    /** Lo que escribiría la cajera (BusquedaEnLaCopiaTests.Textos) con este catálogo, en su orden. */
    val TEXTOS: List<String> = listOf(TEXTOS_1)

    private const val TEXTOS_1 = """
a-z
amarilla
ama
amrailla
aztecas
azt
azetcas
blanca
bla
blnaca
cr2032
cr2
cr0232
carpeta
car
capreta
clips
cli
clpis
corazones
cor
coarzones
cárdenas
cár
cádrenas
duracell
dur
duarcell
escaneo
esc
esacneo
estándar
est
esátndar
forro
for
graduación
gra
grdauación
héroes
hér
héores
jumbo
jum
jubmo
liso
lis
mapa
map
mayas
may
maays
mini
min
naranja
nar
naarnja
números
núm
núemros
palitos
pal
pailtos
pincel
pin
picnel
plumones
plu
plmuones
puebla
pue
pubela
rojo
roj
signos
sig
singos
swingline
swi
swnigline
truper
tru
trpuer
verde
ver
vedre
adhesivas
adh
adehsivas
canario
can
caanrio
colorear
col
coolrear
división
div
diivsión
fluorescente
flu
flourescente
humano
hum
huamno
madera
mad
maedra
nombres
nom
nobmres
permanente
per
pemranente
regalo
reg
reaglo
sobre
sob
sorbe
Plu pun
pun Plu
Pluma Azul
7508
Plu gel
gel Plu
Pluma Café
7509
Mar Sha
Sha Mar
Marcatextos Naranja
Mar par
par Mar
Marcador Verde
7502
Plu col
col Plu
Plumones Caja
7501
Láp col
col Láp
Lápiz Turquesa
7504
Plumón limón
7503
Hoj col
col Hoj
Hojas Rojo
Pap chi
chi Pap
Papel Café
Foa Pli
Pli Foa
Foamy Naranja
Cua pro
pro Cua
Cuaderno juvenil
Cua for
for Cua
Cuaderno Lisa
7507
Cua dib
dib Cua
Cuaderno Francés
Pin acr
acr Pin
Pintura Beige
7500
Pin tex
tex Pin
Pintura Plateado
Pla Cra
Cra Pla
Plastilina Rosa
Lis Pla
Pla Lis
Listón Plateado
P000
Est Ome
Ome Est
Estambre Plateado
7505
Tij esc
esc Tij
Tijeras roma
Mon Olm
Olm Mon
Monografía Olmecas
Lám did
did Lám
Lámina México
Lib par
par Lib
Libro Mandalas
Not adh
adh Not
Notas Amarillo
Fol Oxf
Oxf Fol
Folder Naranja
Coj par
par Coj
Cojín Negro
Bol reg
reg Bol
Bolsa Infantil
Glo Lát
Lát Glo
Globo Rosa
Vel núm
núm Vel
Vela Rosa
Cab USB
USB Cab
Cable USB
Rec Mov
Mov Rec
Recarga Movistar
pluma gel
pl gel
cua pro
folder
lustre plata
negro
surtido
1mm
sobre manial
cuadreno
pluma pumto fino
Pluma  PUNTO fino
lápiz
z
"""

    /** Lo que encontró el POS, hasta 200: juego|texto|elegido (su lugar, -1 sin él)|parecido|lugares (con :sinónimo). */
    val ESPERADO: List<String> = listOf(ESPERADO_1, ESPERADO_2)

    private const val ESPERADO_1 = """
base|a-z|-1|0|1194
base|amarilla|-1|0|360
base|ama|-1|0|1150,427,346,1142,109,404,93,376,635,441,229,833,655,252,253,805,819,1105,1104,1101,1100,390,767,757,360,853,413,1285,331,101,117,747,60,40,274,1158,1166,1295,1305,321,228,1357,362,675,695,709,1456,50,30,294
base|amrailla|-1|1|360
base|aztecas|-1|0|958
base|azt|-1|0|958
base|azetcas|-1|1|958
base|blanca|-1|0|168,166,318,314,313,317,170,172,319,316,1175,1170,174,320,315,1180,1185
base|bla|-1|0|168,1417,166,318,314,1416,594,877,39,1418,595,596,746,606,426,313,1413,674,29,345,607,634,654,317,1412,818,875,872,766,1136,1294,170,172,319,316,804,361,389,273,630,627,1175,1170,412,440,174,320,315,871,876,756,49,293,1407,1409,1403,1405,1180,1185,848,619,620,618,608,852,1072,1284,1304,249,225,832,403,375,873,874,694,708,1070,1071,59
base|blnaca|-1|1|168,166,318,314,313,317,170,172,319,316,1175,1170,174,320,315,1180,1185
base|cr2032|-1|0|1365,1361
base|cr2|-1|0|1365,1361
base|cr0232|-1|1|1365,1361
base|carpeta|-1|0|1188,1173,1175,1178,1179,1172,1170,1174,1182,1189,1177,1176,1171,1180,1181,1183,1184,1187,1185,1186
base|car|-1|0|349,354,346,350,351,1188,345,355,353,352,1173,357,356,1175,1178,1179,1172,1170,1174,1182,1189,1386,1388,1378,1372,1375,360,1177,1176,1171,1180,1181,1183,1184,1187,1185,1186,1385,1384,1387,1389,1379,1381,1380,1383,1382,1373,1374,1377,1376,348,359,358,347,931,926,929,941,1145,925,1160,1139,1140,939,1416,922,1418,1154,1142,924,923,937,418,1138,313,928,419,1144,1219,1222,272,255,930,935,938,936,940,1014,420,417,1159,1143,317,1412,1225,231,927,934,1136,1124,1419,1424,415,423,414,1155,1161,1157,1141,319,1414,1220,248,932,342,1190,1128,413,412,422,315,1226,1223,341,344,1132,421,424,425,416,1158,1156,1221,1224,933,948,461,462,458,459,343,1133
base|capreta|-1|1|1188,1173,1175,1178,1179,1172,1170,1174,1182,1189,1177,1176,1171,1180,1181,1183,1184,1187,1185,1186
base|clips|-1|0|1077,1076,1074,1075
base|cli|-1|0|1077,1076,1074,1075
base|clpis|-1|1|1077,1076,1074,1075
base|corazones|-1|0|1012
base|cor|-1|0|160,162,161,157,165,158,849,846,847,848,850,851,163,164,159,1012
base|coarzones|-1|1|1012
base|cárdenas|-1|0|948
base|cár|-1|0|349,354,346,350,351,1188,345,355,353,352,1173,357,356,1175,1178,1179,1172,1170,1174,1182,1189,1386,1388,1378,1372,1375,360,1177,1176,1171,1180,1181,1183,1184,1187,1185,1186,1385,1384,1387,1389,1379,1381,1380,1383,1382,1373,1374,1377,1376,348,359,358,347,931,926,929,941,1145,925,1160,1139,1140,939,1416,922,1418,1154,1142,924,923,937,418,1138,313,928,419,1144,1219,1222,272,255,930,935,938,936,940,1014,420,417,1159,1143,317,1412,1225,231,927,934,1136,1124,1419,1424,415,423,414,1155,1161,1157,1141,319,1414,1220,248,932,342,1190,1128,413,412,422,315,1226,1223,341,344,1132,421,424,425,416,1158,1156,1221,1224,933,948,461,462,458,459,343,1133
base|cádrenas|-1|1|948
base|duracell|-1|0|1360,1453,1358,1359,1361
base|dur|-1|0|247,1360,271,1453,1358,1359,1361
base|duarcell|-1|1|1360,1453,1358,1359,1361
base|escaneo|-1|0|1426
base|esc|-1|0|1426,256,1068,896,915,1067,898,902,1069,232,914,916,904,1457,900,913,917,906,1035,912
base|esacneo|-1|1|1426
base|estándar|-1|0|1078,1080,1089,1086,1083
base|est|-1|0|1033,1040,1020,1036,1015,1029,1014,1043,1042,1049,862,1023,1037,1012,1048,1027,1046,853,855,867,861,863,860,859,1050,1041,1032,1024,1034,1038,1025,1031,1039,1030,1018,852,866,858,864,857,854,865,856,1045,1016,1026,1035,1013,1019,1021,1044,1028,1047,1051,1017,1022,931,525,941,520,486,582,577,524,491,970,611,615,614,610,557,492,452,613,518,522,554,488,979,612,583,578,576,551,493,490,1218,616,609,521,519,523,552,489,455,1078,630,629,628,550,556,1080,1226,1210,1232,1238,619,620,618,617,580,581,579,584,553,555,487,1089,1086,1083,995
base|esátndar|-1|1|1078,1080,1089,1086,1083
base|forro|-1|0|457,452,450,454,456,455,453,451
base|for|-1|0|457,452,450,454,456,455,453,451,560,602,582,577,603,575,611,594,615,595,596,592,565,558,614,610,606,604,593,586,571,574,613,607,601,605,590,588,572,570,567,566,564,612,583,578,576,559,616,609,589,569,562,563,561,1454,597,587,573,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568
base|graduación|-1|0|1255,1271,1263,1247
base|gra|-1|0|1078,1080,1081,1079,536,603,543,615,592,548,534,604,593,557,535,605,554,544,539,551,541,538,1260,1255,616,552,545,1048,1271,1259,1258,1263,550,556,547,546,537,540,1058,1198,871,1247,1261,1264,1265,1262,617,591,553,555,542,549,784,785,786,787,989,1197,1199,870,791,793,1275
base|grdauación|-1|1|1255,1271,1263,1247
base|héroes|-1|0|955
base|hér|-1|0|955
base|héores|-1|1|955
base|jumbo|-1|0|1077
base|jum|-1|0|1077
base|jubmo|-1|1|1077
base|liso|-1|0|468,467
base|lis|-1|0|809,841,811,839,844,824,813,816,821,818,833,805,804,810,806,819,828,825,835,843,840,812,817,827,826,820,831,822,838,837,834,845,807,815,814,808,829,830,823,832,842,836,502,506,486,482,582,470,603,510,594,530,615,558,534,606,478,588,570,567,564,518,522,554,612,576,526,538,490,498,609,561,1454,494,1246,1254,597,573,514,550,546,1262,618,600,591,585,579,542,474,468,467
base|mapa|-1|0|931,926,929,941,925,939,922,924,923,937,928,930,935,938,936,940,927,934,932,933,1160,1162,1011,1164,1154,1169,1159,1163,1155,1161,1157,1167,1165,1158,1156,1166,1168
base|map|-1|0|931,926,929,941,925,939,922,924,923,937,928,930,935,938,936,940,927,934,932,933,259,178,176,207,889,206,1160,897,1162,1011,1164,888,1154,258,256,887,1169,896,260,272,255,208,1159,1163,263,270,257,1450,211,903,902,165,1155,1161,1157,1167,1165,170,252,253,268,250,269,267,266,265,76,1451,196,177,210,1457,254,1097,1099,195,913,209,1158,1156,1166,1168,171,262,261,249,271,251,264,75,1098,194,912
base|mayas|-1|0|959
base|may|-1|0|959
base|maays|-1|1|959
base|mini|-1|0|1085,1088,1082
base|min|-1|0|88,1444,87,92,90,91,89,1085,1088,1082
base|naranja|-1|0|1152,367,1160,112,104,680,351,418,432,446,1144,824,714,325,772,279,1300,1290,335,810,120,660,55,45,254,838,409,381,65,35,359,858,1168,1310,230,96,1455,395,640,700,752,762,299
base|nar|-1|0|1152,367,1160,112,104,680,351,418,432,446,1144,824,714,325,772,279,1300,1290,335,810,120,660,55,45,254,838,409,381,65,35,359,858,1168,1310,230,96,1455,395,640,700,752,762,299
base|naarnja|-1|1|1152,367,1160,112,104,680,351,418,432,446,1144,824,714,325,772,279,1300,1290,335,810,120,660,55,45,254,838,409,381,65,35,359,858,1168,1310,230,96,1455,395,640,700,752,762,299
base|números|-1|0|1028,983,1064
base|núm|-1|0|1335,1327,1322,1342,1343,1317,1314,1316,1319,1320,1324,1329,1326,1328,1330,1341,1339,1340,1344,1349,1352,1028,983,1064,1315,1321,1318,1325,1323,1333,1331,1332,1337,1334,1336,1338,1345,1346,1347,1348,1353,1350,1351
base|núemros|-1|1|1028,983,1064
base|palitos|-1|0|796,795,797
base|pal|-1|0|796,795,797
base|pailtos|-1|1|796,795,797
base|pincel|-1|0|735,739,736,744,745,738,740,737,741,742,743,734
base|pin|-1|0|638,683,680,735,674,646,635,637,634,644,647,649,654,668,714,739,736,690,688,636,655,670,659,744,745,738,685,684,676,642,660,666,658,669,703,740,737,677,692,652,645,641,639,651,653,672,663,665,1452,662,705,698,741,742,743,734,675,682,681,686,679,691,687,678,689,693,650,643,648,640,657,664,661,656,671,667,673,695,697,694,704,702,700,701,706,699,696,707,709,1456,711,708,717,719,718,716,715,720,713,710,721,712
base|picnel|-1|1|735,739,736,744,745,738,740,737,741,742,743,734
base|plumones|-1|0|188,191,1448,199,1451,196,192,197,198,195,189,190,194,193
base|plu|-1|0|3,28,22,1,4,11,0,37,2,24,39,10,25,63,8,9,16,18,20,26,188,51,29,280,67,6,17,14,21,191,54,33,5,7,279,1448,52,58,55,38,45,276,273,281,199,1451,196,192,60,62,68,65,64,49,35,40,47,46,19,12,13,15,27,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,197,198,195,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43,23,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277,189,190,194,193,161,157,159
base|plmuones|-1|1|188,191,1448,199,1451,196,192,197,198,195,189,190,194,193
base|puebla|-1|0|954
base|pue|-1|0|954
base|pubela|-1|1|954
base|rojo|-1|0|28,22,1140,2,1164,809,10,25,350,778,256,18,366,1188,1219,255,155,6,14,417,431,1148,1211,231,143,380,394,659,54,1173,1289,232,131,751,1178,846,837,151,147,135,127,639,761,64,278,1183,1202,786,857,445,1156,1299,1309,336,326,1203,823,139,1278,408,1355,467,679,699,713,771,34,44,298
base|roj|-1|0|28,22,1140,2,1164,809,10,25,350,778,256,18,366,1188,1219,255,155,6,14,417,431,1148,1211,231,143,380,394,659,54,1173,1289,232,131,751,1178,846,837,151,147,135,127,639,761,64,278,1183,1202,786,857,445,1156,1299,1309,336,326,1203,823,139,1278,408,1355,467,679,699,713,771,34,44,298
base|signos|-1|0|999
base|sig|-1|0|999
base|singos|-1|1|999
base|swingline|-1|0|1078,1083,1084,1082,1079
base|swi|-1|0|1078,1083,1084,1082,1079
base|swnigline|-1|1|1078,1083,1084,1082,1079
base|truper|-1|0|792,793
base|tru|-1|0|792,793
base|trpuer|-1|1|792,793
base|verde|-1|0|3,638,242,11,340,349,107,99,118,63,110,94,1149,144,1283,649,148,33,7,357,1157,1165,1141,267,266,265,243,128,102,658,669,770,760,1179,1174,1189,430,1298,1288,1308,323,1208,241,822,140,132,115,365,698,19,15,297,288,1184,851,780,856,416,444,333,330,1224,1216,808,836,1241,152,156,136,123,407,379,393,678,689,712,750,53,43,308,277
base|ver|-1|0|3,638,242,11,340,349,107,239,99,118,63,110,94,1149,144,1283,649,263,148,33,7,357,1157,1165,1141,267,266,265,243,128,102,972,658,669,770,760,1179,1174,1189,430,1298,1288,1308,323,1208,241,822,140,132,115,365,698,19,15,297,288,1184,851,780,1410,1411,856,416,444,333,330,1000,988,1224,1216,808,836,1241,152,156,136,123,407,379,393,678,689,712,750,53,43,308,277
base|vedre|-1|1|3,638,242,11,340,349,107,99,118,63,110,94,1149,144,1283,649,148,33,7,357,1157,1165,1141,267,266,265,243,128,102,658,669,770,760,1179,1174,1189,430,1298,1288,1308,323,1208,241,822,140,132,115,365,698,19,15,297,288,1184,851,780,856,416,444,333,330,1224,1216,808,836,1241,152,156,136,123,407,379,393,678,689,712,750,53,43,308,277
base|adhesivas|-1|0|1103,1105,1104,1101,1100,1111,1107,1106,1110,1108,1109,1102
base|adh|-1|0|452,885,881,1116,1283,1113,450,454,1103,455,453,882,1281,1105,1104,1101,1100,1111,1118,1119,1120,884,886,1280,1107,1106,1110,1108,1109,1115,1117,1112,1114,451,883,878,879,880,1279,1278,1282,1102
base|adehsivas|-1|1|1103,1105,1104,1101,1100,1111,1107,1106,1110,1108,1109,1102
base|canario|-1|0|252,338,328,228
base|can|-1|0|1121,1125,1129,252,1386,1388,1385,1384,1387,1389,338,328,228
base|caanrio|-1|1|252,338,328,228
base|colorear|-1|0|1060,1063,1058,1054,1052,1055,1053,1056,1066,1062,1064,1059,1057,1061,1065
base|col|-1|0|207,206,200,203,216,201,202,208,1450,211,215,212,1447,213,210,204,205,209,217,214,1443,242,259,340,729,239,334,258,256,227,245,188,329,260,234,226,233,1415,272,255,238,247,240,235,191,728,325,263,270,257,229,231,1448,730,224,1419,335,322,1414,252,253,268,250,269,267,266,265,237,248,232,243,1060,199,1451,196,192,732,733,1375,331,339,337,324,323,986,254,236,241,1063,1058,1054,197,198,195,731,1385,1387,1389,1379,1381,1383,1373,1377,1073,338,336,332,333,321,328,327,326,330,262,261,249,271,251,264,228,225,244,230,246,1052,1055,1053,1056,1066,1062,1064,1059,1057,1061,1065,189,190,194,193
base|coolrear|-1|1|1060,1063,1058,1054,1052,1055,1053,1056,1066,1062,1064,1059,1057,1061,1065
base|división|-1|0|922,932,1192,1193
base|div|-1|0|922,932,1192,1193
base|diivsión|-1|1|922,932,1192,1193
base|fluorescente|-1|0|357,360,359,358
base|flu|-1|0|357,360,359,358
base|flourescente|-1|1|357,360,359,358
base|humano|-1|0|966
base|hum|-1|0|966
base|huamno|-1|1|966
base|madera|-1|0|796,795,797
base|mad|-1|0|796,795,1268,797
base|maedra|-1|1|796,795,797
base|nombres|-1|0|924,934
base|nom|-1|0|924,934
base|nobmres|-1|1|924,934
base|permanente|-1|0|126,125,129,130,131,128,140,135,132,127,137,138,139,133,134,136
base|per|-1|0|1091,1097,1099,1092,1093,1098,1094,1095,1096,126,125,129,130,131,128,140,135,132,127,137,138,139,133,134,136
base|pemranente|-1|1|126,125,129,130,131,128,140,135,132,127,137,138,139,133,134,136
base|regalo|-1|0|1248,1257,1260,1253,1255,1252,1245,1243,1246,1244,1259,1258,1263,1254,1242,1247,1249,1261,1264,1265,1262,1251,1250,1256
base|reg|-1|0|893,890,889,888,887,891,892,894,895,1248,1257,1260,1253,1255,1252,1245,1243,1246,1244,1259,1258,1263,1254,1007,1242,1247,1249,1261,1264,1265,1262,1251,1250,1256
base|reaglo|-1|1|1248,1257,1260,1253,1255,1252,1245,1243,1246,1244,1259,1258,1263,1254,1242,1247,1249,1261,1264,1265,1262,1251,1250,1256
base|sobre|-1|0|1135,1137,1136,1134,1133,778,776,782,779,777,781,783,780
base|sob|-1|0|1135,1137,1136,1134,1133,778,776,782,779,777,781,783,780
base|sorbe|-1|1|778,776,1135,1137,1136,1134,782,779,777,781,783,780,1133
base|Plu pun|-1|0|3,28,22,1,4,11,0,2,24,10,25,8,9,16,18,20,26,280,6,17,14,21,5,7,279,276,273,281,19,12,13,15,27,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,23,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277
base|pun Plu|-1|0|3,28,22,1,4,11,0,2,24,10,25,8,9,16,18,20,26,280,6,17,14,21,5,7,279,276,273,281,19,12,13,15,27,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,23,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277
base|Pluma Azul|-1|0|4,0,8,16,20,26,52,62,12,32,42,23
base|7508|-1|0|525,1152,77,0,1140,200,10,565,513,548,484,334,104,748,220,610,593,1169,51,915,588,567,898,14,202,1148,454,105,1365,739,616,563,497,1161,744,792,789,727,914,911,628,1399,905,904,550,540,496,320,324,254,1233,47,86,633,1409,1403,1405,1377,1076,1074,906,159,581,1088,858,865,1212,883,225,156,136,678,667,702,717,716,793,775,30,302,724
base|Plu gel|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|gel Plu|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|Pluma Café|-1|0|58,38,68,48
base|7509|-1|0|520,476,470,512,536,314,2,729,594,222,534,1142,104,8,887,234,94,280,201,605,488,141,634,714,67,6,191,583,541,772,7,788,1125,211,215,902,489,1167,455,253,243,73,120,660,770,281,1111,921,1170,210,597,587,532,546,1080,1081,339,1223,1208,151,142,147,114,95,92,1238,1360,1363,645,665,662,747,758,60,68,12,297,1408,918,1180,1112,1114,164,223,579,631,474,1082,856,336,321,1369,1236,343,1095,643,657,661,673,712,765,48,41,23,299,312,275,895
base|Mar Sha|-1|0|126,98,99,94,125,93,100,128,127,97,95,96
base|Sha Mar|-1|0|126,98,99,94,125,93,100,128,127,97,95,96
base|Marcatextos Naranja|-1|0|112,104,120,96,1455
base|Mar par|-1|0|155,141,144,149,143,145,148,154,151,142,147,150,152,153,156,146
base|par Mar|-1|0|155,141,144,149,143,145,148,154,151,142,147,150,152,153,156,146
base|Marcador Verde|-1|0|144,148,128,140,132,152,156,136
base|7502|-1|0|74,509,3,502,4,186,506,486,1139,24,528,558,20,606,590,554,544,539,1151,1219,272,144,649,17,1113,1148,229,145,730,1127,609,523,316,322,131,685,767,753,38,45,273,894,187,630,1398,1392,1388,1378,1119,556,320,1226,886,119,90,463,651,64,35,307,198,183,917,1183,1379,1115,1131,1076,555,1410,1087,173,338,230,152,138,133,123,1366,1102,1098,1094,734,675,650,671,701,752,295,286,1193
base|Plu col|-1|0|188,191,1448,199,1451,196,192,197,198,195,189,190,194,193
base|col Plu|-1|0|188,191,1448,199,1451,196,192,197,198,195,189,190,194,193
base|Plumones Caja|-1|0|188,191,1448,199,1451,196,192,197,198,195,189,190,194,193
base|7501|-1|0|4,207,318,113,25,99,26,604,646,80,1188,1116,570,100,654,526,551,493,270,1103,766,722,165,562,169,250,265,237,76,120,88,738,642,760,55,276,192,177,181,733,627,1390,1172,1189,1118,573,547,236,127,115,87,1107,1097,12,27,278,292,195,1187,1384,1387,1376,1132,1074,622,1090,1156,171,338,326,262,249,153,134,1231,458,1092,648,697,721,769,771,755,290,910
base|Láp col|-1|0|242,259,239,258,256,227,245,260,234,226,233,272,255,238,247,240,235,263,270,257,229,231,252,253,268,250,269,267,266,265,237,248,232,243,254,236,241,262,261,249,271,251,264,228,225,244,230,246
base|col Láp|-1|0|242,259,239,258,256,227,245,260,234,226,233,272,255,238,247,240,235,263,270,257,229,231,252,253,268,250,269,267,266,265,237,248,232,243,254,236,241,262,261,249,271,251,264,228,225,244,230,246
base|Lápiz Turquesa|-1|0|240,264
base|7504|-1|0|508,78,167,481,178,1160,1139,897,1162,611,543,1164,113,39,531,16,614,1138,329,260,735,674,1149,240,647,21,891,728,219,538,498,1218,231,69,690,774,5,521,505,1157,1147,170,267,248,73,154,1091,676,52,789,1397,1122,205,867,861,863,323,1205,135,1229,341,639,756,40,19,15,306,289,726,725,1407,1196,1400,1396,1176,1184,1380,1373,214,163,624,621,591,487,1158,171,332,330,1213,1204,261,139,103,1371,1237,459,343,1358,1361,681,689,704,706,707,708,719,750,66,36,294,300,189
base|Plumón limón|-1|0|288,308
base|7503|-1|0|74,28,1,889,491,112,483,98,877,256,227,746,18,480,901,885,881,226,71,110,108,601,572,175,635,208,490,1163,263,875,655,185,589,519,552,545,494,862,1161,1165,335,1206,882,252,1101,1100,684,666,757,1178,855,859,174,315,337,1217,97,117,1232,663,768,62,311,274,282,288,731,1108,919,1177,217,618,580,549,499,1083,1084,1158,333,327,1224,879,271,116,103,460,1362,694,715,749,296,309,308,193
base|Hoj col|-1|0|340,334,329,325,335,322,331,339,337,324,323,338,336,332,333,321,328,327,326,330
base|col Hoj|-1|0|340,334,329,325,335,322,331,339,337,324,323,338,336,332,333,321,328,327,326,330
base|Hojas Rojo|-1|0|336,326
base|Pap chi|-1|0|376,385,380,382,377,378,383,381,375,384,386,387,388,379
base|chi Pap|-1|0|376,385,380,382,377,378,383,381,375,384,386,387,388,379
base|Papel Café|-1|0|370,398,384
base|Foa Pli|-1|0|428,427,435,434,429,426,432,438,439,433,431,430,437,436
base|Pli Foa|-1|0|428,427,435,434,429,426,432,438,439,433,431,430,437,436
base|Foamy Naranja|-1|0|418,432,446
base|Cua pro|-1|0|509,508,525,520,507,502,476,481,506,486,482,473,470,529,512,516,536,501,524,510,491,528,530,503,543,483,472,475,531,513,548,534,484,511,517,557,535,492,500,479,480,478,477,518,522,554,544,539,488,527,526,551,541,538,493,490,498,485,471,521,519,523,533,505,552,545,489,497,495,1446,494,532,514,504,550,556,547,546,537,540,496,623,621,515,553,555,542,549,487,499,474
base|pro Cua|-1|0|509,508,525,520,507,502,476,481,506,486,482,473,470,529,512,516,536,501,524,510,491,528,530,503,543,483,472,475,531,513,548,534,484,511,517,557,535,492,500,479,480,478,477,518,522,554,544,539,488,527,526,551,541,538,493,490,498,485,471,521,519,523,533,505,552,545,489,497,495,1446,494,532,514,504,550,556,547,546,537,540,496,623,621,515,553,555,542,549,487,499,474
base|Cuaderno juvenil|-1|0|508,520,476,560,602,512,516,536,575,524,611,528,472,596,548,484,614,593,492,500,480,605,590,572,566,544,488,578,569,563,552,587,532,504,556,540,496,620,617,608,599,581,584
base|Cua for|-1|0|560,602,582,577,603,575,611,594,615,595,596,592,565,558,614,610,606,604,593,586,571,574,613,607,601,605,590,588,572,570,567,566,564,612,583,578,576,559,454,616,609,589,569,562,563,561,1454,597,587,573,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568,451
base|for Cua|-1|0|560,602,582,577,603,575,611,594,615,595,596,592,565,558,614,610,606,604,593,586,571,574,613,607,601,605,590,588,572,570,567,566,564,612,583,578,576,559,454,616,609,589,569,562,563,561,1454,597,587,573,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568,451
base|Cuaderno Lisa|-1|0|502,506,486,482,582,470,603,510,594,530,615,558,534,606,478,588,570,567,564,518,522,554,612,576,526,538,490,498,609,561,1454,494,597,573,514,550,546,618,600,591,585,579,542,474
base|7507|-1|0|638,890,259,176,206,37,2,530,25,615,595,596,118,680,26,571,492,478,313,329,233,1149,238,155,6,576,485,1159,149,148,659,1173,1129,213,1147,322,268,269,266,76,130,102,122,1104,342,751,199,1386,1128,204,537,1153,331,241,115,111,101,119,1227,876,677,653,705,293,301,1192,1194,908,1110,1385,1381,1117,1201,221,553,542,1411,1156,1168,336,330,1221,1207,228,137,1368,1228,741,743,686,656,699,720,710,790,59,912
base|Cua dib|-1|0|624,623,622,621
base|dib Cua|-1|0|624,623,622,621
base|Cuaderno Francés|-1|0|602,603,611,594,615,595,596,592,614,610,606,604,593,586,613,607,601,605,590,588,612,616,609,589,597,587,624,622,619,620,618,617,608,600,598,599,591,585
base|Pin acr|-1|0|638,683,680,674,646,635,637,634,644,647,649,654,668,690,688,636,655,670,659,685,684,676,642,660,666,658,669,677,692,652,645,641,639,651,653,672,663,665,1452,662,675,682,681,686,679,691,687,678,689,693,650,643,648,640,657,664,661,656,671,667,673
base|acr Pin|-1|0|638,683,680,674,646,635,637,634,644,647,649,654,668,690,688,636,655,670,659,685,684,676,642,660,666,658,669,677,692,652,645,641,639,651,653,672,663,665,1452,662,675,682,681,686,679,691,687,678,689,693,650,643,648,640,657,664,661,656,671,667,673
base|Pintura Beige|-1|0|692,652,672
base|7500|-1|0|1146,507,893,11,582,501,1162,683,126,334,216,477,29,161,613,518,1144,125,668,892,527,1159,1163,1143,1211,257,736,54,625,224,1124,1123,569,1155,319,232,745,58,196,1190,1402,1179,1120,900,1215,1210,884,344,1099,1364,641,672,65,49,1406,1404,1109,1195,209,907,619,620,608,599,584,568,1086,1166,451,264,246,150,146,459,874,682,693,709,53,31,305,283
base|Pin tex|-1|0|714,703,705,698,695,697,694,704,702,700,701,706,699,696,707,709,1456,711,708,717,719,718,716,715,720,713,710,721,712
base|tex Pin|-1|0|714,703,705,698,695,697,694,704,702,700,701,706,699,696,707,709,1456,711,708,717,719,718,716,715,720,713,710,721,712
base|Pintura Plateado|-1|0|646,666,686,706,720
base|Pla Cra|-1|0|773,766,774,772,767,770,768,769,775,771
base|Cra Pla|-1|0|773,766,774,772,767,770,768,769,775,771
base|Plastilina Rosa|-1|0|748,768,758
base|Lis Pla|-1|0|844,816,830
base|Pla Lis|-1|0|844,816,830
base|Listón Plateado|-1|0|844,816,830
base|P000|-1|0|931,941,975,962,367,428,977,1011,939,427,435,809,922,346,350,778,457,923,958,980,968,401,776,429,928,404,1335,1040,1036,1067,419,446,439,824,930,935,936,963,979,1283,376,385,372,1137,1257,355,352,1015,1042,417,433,456,985,813,816,821,934,959,371,402,1134,1277,1327,1260,357,1450,1446,1023,1069,415,1300,1301,1290,1307,1004,1002,1009,1060,805,804,819,828,825,835,840,932,973,951,949,1354,377,389,1271,1342,1245,1243,1246,1244,1258,1263,1254,1457,846,1449,1050,1041,1032,1024,1038,1025,1031,1039,1018,413,412,422,440,448,447,430,1285,1287,1312,1308,986,993,987,997,1006,1007,1008,1058,812,817,827,826,820,831,822,838,837,1444,965,950,952,1280,795,405,378,383,364,373,363,365,469,1268,1269,1270,1319,1320,1329,1326,1330,1341,1339,1340,1344,1349,798,799,1249,1264,1265,1262,1251,1250,348,358,847,848,1445,784,785,779,777,781,1045,1016,1026,1013,1021,1022,425,445,436,1295,1297,1303,1302,1299
base|Est Ome|-1|0|862,853,855,867,861,863,860,859,852,866,858,864,857,854,865,856
base|Ome Est|-1|0|862,853,855,867,861,863,860,859,852,866,858,864,857,854,865,856
base|Estambre Plateado|-1|0|864
base|7505|-1|0|22,482,473,340,577,314,503,10,592,188,586,511,535,479,109,108,162,564,522,1151,93,578,450,317,1209,143,688,7,182,723,158,561,319,1220,128,669,184,916,920,1182,1375,1077,504,853,324,899,871,737,764,291,284,285,197,83,84,913,632,1195,1401,1391,1171,1181,1383,1075,1200,600,585,852,864,854,1072,1073,1079,880,251,123,1370,89,1230,461,462,1359,679,664,696,718,713,791,762,61,50,56,34,42,44,43,304,303,190,85,79
base|Tij esc|-1|0|915,914,916,913,917,912
base|esc Tij|-1|0|915,914,916,913,917,912
base|Tijeras roma|-1|0|914,916,912
base|Mon Olm|-1|0|960
base|Olm Mon|-1|0|960
base|Monografía Olmecas|-1|0|960
base|Lám did|-1|0|1011,1010,982,985,1004,991,1002,1009,986,993,998,987,997,1006,1007,1008,1005,989,990,1003,992,995,994,983,996,999,1001,984,1000,988
base|did Lám|-1|0|1011,1010,982,985,1004,991,1002,1009,986,993,998,987,997,1006,1007,1008,1005,989,990,1003,992,995,994,983,996,999,1001,984,1000,988
base|Lámina México|-1|0|1011,1007,1008
base|Lib par|-1|0|457,456,1060,1063,1058,1054,1052,1055,1053,1056,1066,1062,1064,1059,1057,1061,1065
base|par Lib|-1|0|457,456,1060,1063,1058,1054,1052,1055,1053,1056,1066,1062,1064,1059,1057,1061,1065
base|Libro Mandalas|-1|0|1056
base|Not adh|-1|0|1103,1105,1104,1101,1100,1107,1106,1102
base|adh Not|-1|0|1103,1105,1104,1101,1100,1107,1106,1102
base|Notas Amarillo|-1|0|1105,1104,1101,1100
base|Fol Oxf|-1|0|1152,1146,1145,1150,1139,1140,1142,1138,1144,1151,1149,1143,1148,1141,1147,1153
base|Oxf Fol|-1|0|1152,1146,1145,1150,1139,1140,1142,1138,1144,1151,1149,1143,1148,1141,1147,1153
base|Folder Naranja|-1|0|1152,1160,1144,1168
base|Coj par|-1|0|1201,1200,1202
base|par Coj|-1|0|1201,1200,1202
base|Cojín Negro|-1|0|1200
base|Bol reg|-1|0|1248,1257,1260,1253,1255,1252,1245,1243,1246,1244,1259,1258,1263,1254,1242,1247,1249,1261,1264,1265,1262,1251,1250,1256
base|reg Bol|-1|0|1248,1257,1260,1253,1255,1252,1245,1243,1246,1244,1259,1258,1263,1254,1242,1247,1249,1261,1264,1265,1262,1251,1250,1256
base|Bolsa Infantil|-1|0|1248,1264,1256
base|Glo Lát|-1|0|1294,1300,1301,1290,1289,1298,1285,1287,1286,1288,1295,1297,1303,1302,1299,1296,1284,1293,1292,1291
base|Lát Glo|-1|0|1294,1300,1301,1290,1289,1298,1285,1287,1286,1288,1295,1297,1303,1302,1299,1296,1284,1293,1292,1291
base|Globo Rosa|-1|0|1286,1296,1306
base|Vel núm|-1|0|1335,1327,1322,1342,1343,1317,1314,1316,1319,1320,1324,1329,1326,1328,1330,1341,1339,1340,1344,1349,1352,1315,1321,1318,1325,1323,1333,1331,1332,1337,1334,1336,1338,1345,1346,1347,1348,1353,1350,1351
base|núm Vel|-1|0|1335,1327,1322,1342,1343,1317,1314,1316,1319,1320,1324,1329,1326,1328,1330,1341,1339,1340,1344,1349,1352,1315,1321,1318,1325,1323,1333,1331,1332,1337,1334,1336,1338,1345,1346,1347,1348,1353,1350,1351
base|Vela Rosa|-1|0|1316,1320,1324,1328,1340,1344,1352,1332,1336,1348
base|Cab USB|-1|0|1398,1399,1397,1392,1393,1390,1400,1401,1396,1394,1395,1391
base|USB Cab|-1|0|1398,1399,1397,1392,1393,1390,1400,1401,1396,1394,1395,1391
base|Cable USB|-1|0|1398,1399,1397,1392,1393,1390,1400,1401,1396,1394,1395,1391
base|Rec Mov|-1|0|1440,1441,1439,1442
base|Mov Rec|-1|0|1440,1441,1439,1442
base|Recarga Movistar|-1|0|1440,1441,1439,1442
base|pluma gel|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|pl gel|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|cua pro|-1|0|509,508,525,520,507,502,476,481,506,486,482,473,470,529,512,516,536,501,524,510,491,528,530,503,543,483,472,475,531,513,548,534,484,511,517,557,535,492,500,479,480,478,477,518,522,554,544,539,488,527,526,551,541,538,493,490,498,485,471,521,519,523,533,505,552,545,489,497,495,1446,494,532,514,504,550,556,547,546,537,540,496,623,621,515,553,555,542,549,487,499,474
base|folder|-1|0|1152,1146,1145,1150,1160,1139,1162,1140,1164,1154,1142,1169,1138,1144,1151,1149,1159,1163,1143,1148,1155,1161,1157,1167,1165,1141,1147,1153,1158,1156,1166,1168
base|lustre plata|-1|0|401
base|negro|-1|0|1,1417,24,1416,126,1418,9,1413,226,811,839,280,419,773,17,21,352,433,1412,763,5,1301,250,825,154,130,382,753,1402,1386,1388,1378,1372,859,447,1311,1205,142,641,46,13,27,1406,1408,1404,1176,1171,1181,1186,1384,1380,1382,1374,1376,1200,850,1291,1221,1213,150,146,138,134,1228,1234,410,368,396,681,661,701,715,66,56,36,300
base|surtido|-1|0|729,188,191,728,1103,1448,730,224,199,1451,196,192,732,733,986,1107,1106,197,198,195,731,1073,1199,794,1102,189,190,194,193
base|1mm|-1|0|
base|sobre manial|-1|1|1134,1133
base|cuadreno|-1|1|509,508,525,520,507,502,476,560,481,506,486,482,473,602,582,470,577,529,512,516,536,501,603,575,524,510,491,611,594,528,530,503,543,483,472,475,615,595,596,592,565,558,531,513,548,534,484,614,610,606,604,593,586,571,574,511,517,557,535,492,500,479,480,478,477,613,607,601,605,590,588,572,570,567,566,564,518,522,554,544,539,488,612,583,578,576,559,527,526,551,541,538,493,490,498,485,471,616,609,589,569,562,563,561,1454,521,519,523,533,505,552,545,489,497,495,1446,494,597,587,573,532,514,504,550,556,547,546,537,540,496,624,623,622,621,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568,631,515,553,555,542,549,487,499,474
base|pluma pumto fino|-1|1|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15,280,279,276,273,281,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277
base|Pluma  PUNTO fino|-1|0|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15
base|lápiz|-1|0|74,77,72,78,242,259,239,70,258,256,227,245,885,881,260,234,226,233,71,272,255,238,247,240,235,263,270,257,229,231,69,882,252,253,268,250,269,267,266,265,237,248,232,243,76,73,884,886,254,236,241,883,878,879,880,262,261,249,271,251,264,228,225,244,230,246,75
base|z|-1|0|39,118,14,124,122,120,45,117,119,40,47,46,12,13,15,1194,121,1455,123,946,42,48,44,41,43
base|gel plu|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|unto fino|-1|1|3,1,4,11,0,2,10,8,9,16,18,280,6,17,14,5,7,279,276,273,281,19,12,13,15,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277
base|pluma punto fino|-1|0|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15
base|PLUMA PUNTO FINO|-1|0|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15
base|\u0020Pluma  PUNTO fino|-1|0|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15
base|boligrafo punto fino|-1|0|
base|bolig gel|-1|0|
base|bol gel|-1|0|
base|rotulador|-1|0|
base|rotul|-1|0|
base|plumon|-1|0|188,280,191,279,1448,276,273,281,199,1451,196,192,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,197,198,195,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277,189,190,194,193
base|plumon rotulador|-1|0|
base|rotulador negro|-1|0|
base|zanfona|-1|0|
base|ocarina|-1|0|
base|engar orden|-1|0|
base|lap az|-1|0|239,238,263,1220,237,1212,1204,262,261
base|lus az|-1|0|392
base|zz-caso|-1|0|
base|ZZ-CASO-01|-1|0|
"""

    private const val ESPERADO_2 = """
base|cuaderno|-1|0|509,508,525,520,507,502,476,560,481,506,486,482,473,602,582,470,577,529,512,516,536,501,603,575,524,510,491,611,594,528,530,503,543,483,472,475,615,595,596,592,565,558,531,513,548,534,484,614,610,606,604,593,586,571,574,511,517,557,535,492,500,479,480,478,477,613,607,601,605,590,588,572,570,567,566,564,518,522,554,544,539,488,612,583,578,576,559,527,526,551,541,538,493,490,498,485,471,616,609,589,569,562,563,561,1454,521,519,523,533,505,552,545,489,497,495,1446,494,597,587,573,532,514,504,550,556,547,546,537,540,496,624,623,622,621,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568,631,515,553,555,542,549,487,499,474
base|pl|-1|0|3,28,22,1,4,11,0,37,2,24,39,10,25,746,748,63,8,9,16,18,20,26,188,51,29,280,773,67,6,17,14,21,191,766,774,772,763,54,33,5,7,279,1448,767,770,754,753,751,757,760,52,58,55,38,45,276,273,281,199,1451,196,192,803,768,747,756,764,761,758,60,62,68,65,64,49,35,40,47,46,19,12,13,15,27,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,197,198,195,802,769,775,771,749,755,752,750,759,765,762,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43,23,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277,189,190,194,193,428,427,435,434,457,401,429,426,432,438,844,646,1335,161,439,157,433,431,454,456,816,1327,455,453,991,1002,1281,666,1343,430,373,1319,1339,159,785,777,864,424,437,436,830,974,387,686,706
base|pluma|-1|0|3,28,22,1,4,11,0,37,2,24,39,10,25,63,8,9,16,18,20,26,51,29,67,6,17,14,21,54,33,5,7,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,19,12,13,15,27,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43,23,161,157,159
base|pluma g|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|cua|-1|0|509,508,525,520,507,502,476,560,481,506,486,482,473,602,582,470,577,529,512,516,536,501,603,575,524,510,491,611,594,528,530,503,543,483,472,475,615,595,596,592,565,558,531,513,548,534,484,614,610,606,604,593,586,571,574,511,517,557,535,492,500,479,480,478,477,613,607,601,605,590,588,572,570,567,566,564,518,522,554,544,539,488,612,583,578,576,559,527,526,551,541,538,493,490,498,485,471,616,609,589,569,562,563,561,1454,521,519,523,533,505,552,545,489,497,495,1446,494,597,587,573,532,514,504,550,556,547,546,537,540,496,624,623,622,621,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568,631,515,553,555,542,549,487,499,474,454,629,626,451
base|750|-1|0|74,509,508,525,3,1152,77,1146,520,507,78,28,1145,638,22,502,476,167,1,4,893,890,186,481,11,506,486,482,473,259,0,178,176,207,582,470,340,889,206,577,512,516,536,501,1160,1139,897,37,603,524,491,1162,1140,166,318,314,112,107,683,2,24,729,200,160,611,594,528,530,503,543,483,472,1164,70,126,113,98,877,39,10,25,888,222,615,595,596,592,565,558,531,513,548,534,484,1142,334,258,256,227,245,106,104,99,118,680,746,748,63,8,16,18,20,26,188,887,1126,216,220,614,610,606,604,593,586,571,574,511,557,535,492,500,479,480,478,477,1169,1138,313,329,901,896,885,881,260,234,226,233,71,109,110,108,94,735,674,646,51,29,280,80,915,1188,1116,1121,201,162,161,613,607,601,605,590,588,572,570,567,566,564,518,522,554,544,539,488,1144,1151,1149,175,898,1219,272,255,238,247,240,155,141,144,125,93,100
base|p0000|-1|0|367,428,427,435,809,346,350,778,457,401,776,429,404,419,446,439,824,376,385,372,355,352,417,433,456,813,816,821,371,402,357,415,805,804,819,828,825,835,840,377,389,846,413,412,422,440,448,447,430,812,817,827,826,820,831,822,838,837,795,405,378,383,364,373,363,365,469,798,799,348,358,847,848,784,785,779,777,781,425,445,436,807,815,814,832,842,836,797,406,403,410,407,375,386,379,362,396,393,467
base|lapiz|-1|0|74,77,72,78,242,259,239,70,258,256,227,245,885,881,260,234,226,233,71,272,255,238,247,240,235,263,270,257,229,231,69,882,252,253,268,250,269,267,266,265,237,248,232,243,76,73,884,886,254,236,241,883,878,879,880,262,261,249,271,251,264,228,225,244,230,246,75
base|LÁPIZ|-1|0|74,77,72,78,242,259,239,70,258,256,227,245,885,881,260,234,226,233,71,272,255,238,247,240,235,263,270,257,229,231,69,882,252,253,268,250,269,267,266,265,237,248,232,243,76,73,884,886,254,236,241,883,878,879,880,262,261,249,271,251,264,228,225,244,230,246,75
base|lápiz \u0020|-1|0|74,77,72,78,242,259,239,70,258,256,227,245,885,881,260,234,226,233,71,272,255,238,247,240,235,263,270,257,229,231,69,882,252,253,268,250,269,267,266,265,237,248,232,243,76,73,884,886,254,236,241,883,878,879,880,262,261,249,271,251,264,228,225,244,230,246,75
base|\u0020\u0020|-1|0|
base||-1|0|
base|·|-1|0|931,509,508,525,926,929,1152,1146,941,520,507,1145,502,476,560,481,1150,506,486,482,473,602,582,470,925,577,529,512,516,536,501,428,1160,1139,603,575,524,510,491,1162,1140,939,611,594,528,530,503,543,483,472,475,427,435,434,1164,809,922,615,595,596,592,565,558,531,513,548,534,484,1154,1142,841,924,923,937,614,610,606,604,593,586,571,574,511,517,557,535,492,500,479,480,478,477,418,429,426,432,438,1169,1138,1313,811,839,844,928,1335,1188,613,607,601,605,590,588,572,570,567,566,564,518,522,554,544,539,488,419,439,1144,1151,1149,1219,1222,824,930,935,938,936,940,1248,1257,612,583,578,576,559,527,526,551,541,538,493,490,498,485,471,420,417,433,431,1159,1163,1143,1148,1225,1218,1211,1209,813,816,821,818,833,927,934,1103,1327,1260,1253,1255,1252,1173,616,609,589,569,562,563,561,1454,521,519,523,533,505,552,545,489,497,495,1446,494,415
base|colores|-1|0|207,206,200,203,216,201,202,208,1450,211,215,212,1447,213,210,204,205,209,217,214,1443,729,188,191,728,1448,730,224,199,1451,196,192,732,733,986,197,198,195,731,1073,189,190,194,193
base|azul|-1|0|4,0,1139,239,113,457,334,8,16,20,26,429,392,238,141,125,637,1163,263,821,149,145,105,690,670,415,1155,1147,1307,1220,237,835,129,52,276,1172,1182,855,867,1287,324,97,1227,1233,378,364,677,62,12,289,1317,1329,1341,1349,1177,1187,348,1201,847,779,443,1297,1212,1204,262,261,807,1239,153,137,133,121,1279,406,1356,468,650,657,697,711,769,749,759,32,42,23,296,309,1321,1325,1333,1337,1345,1353
base|negro fino|-1|0|1,9,280,17,5,13,300
base|papel|-1|0|367,401,404,366,392,376,385,372,466,380,370,371,394,402,1354,382,377,361,369,390,389,391,342,409,405,378,383,381,364,373,363,365,463,398,399,341,344,469,465,406,403,410,408,407,1357,1356,1355,375,384,386,387,388,379,362,368,374,461,462,460,458,459,411,400,397,395,396,393,343,464,468,467
base|pegamento|-1|0|877,875,872,871,876,873,874
base|tijeras|-1|0|915,914,916,913,917,912
base|marcador|-1|0|126,155,141,144,125,149,143,145,148,154,129,130,131,128,151,142,147,140,135,132,127,150,152,153,156,146,137,138,139,133,134,136
base|regla 30|-1|0|889,888,891,892,894,895
base|liston ROJO|-1|0|809,837,823
base|lapicero|-1|1|1219,1222,1225,1218,1211,1209,1220,1206,1226,1223,1217,1215,1214,1210,1205,1208,1221,1224,1212,1213,1216,1204,1207,1203
base|plumero de avestruz|-1|0|
base|Pluma punto|-1|0|3,28,22,1,4,11,0,2,24,10,25,8,9,16,18,20,26,6,17,14,21,5,7,19,12,13,15,27,23
base|Pluma de|-1|0|37,39,63,51,29,67,54,33,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43
base|Lápiz del|-1|0|74,77,72,78,70,71,69,76,73,75
base|Marcatextos Sharpie|-1|0|98,99,94,93,100,97,95,96
base|Marcador permanente|-1|0|126,125,129,130,131,128,140,135,132,127,137,138,139,133,134,136
base|Marcador para|-1|0|155,141,144,149,143,145,148,154,151,142,147,150,152,153,156,146
base|Goma de|-1|0|168,167,166,175,169,170,172,174,171,173
base|Colores Crayola|-1|0|200,201,202,188,732,733,189,190
base|Lápiz de|-1|0|74,77,72,78,242,259,239,70,258,256,227,245,260,234,226,233,71,272,255,238,247,240,235,263,270,257,229,231,69,252,253,268,250,269,267,266,265,237,248,232,243,76,73,254,236,241,262,261,249,271,251,264,228,225,244,230,246,75
base|Plumón punto|-1|0|280,279,276,273,281,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277
base|Hojas de|-1|0|340,334,329,325,335,322,331,339,337,324,323,338,336,332,333,321,328,327,326,330,625,1190,630,629,628,627,626,1191
base|Cartulina Rojo|-1|0|350
base|Papel china|-1|0|376,385,380,382,377,378,383,381,375,384,386,387,388,379
base|Papel lustre|-1|0|401,392,394,402,390,389,391,398,399,400,397,395,396,393
base|Foamy Carta|-1|0|418,419,420,417,415,423,414,413,412,422,421,424,425,416
base|Forro adherible|-1|0|452,450,454,455,453,451
base|Cuaderno profesional|-1|0|509,508,525,520,507,502,476,481,506,486,482,473,470,529,512,516,536,501,524,510,491,528,530,503,543,483,472,475,531,513,548,534,484,511,517,557,535,492,500,479,480,478,477,518,522,554,544,539,488,527,526,551,541,538,493,490,498,485,471,521,519,523,533,505,552,545,489,497,495,1446,494,532,514,504,550,556,547,546,537,540,496,515,553,555,542,549,487,499,474,623,621
base|Cuaderno forma|-1|0|560,602,582,577,603,575,611,594,615,595,596,592,565,558,614,610,606,604,593,586,571,574,613,607,601,605,590,588,572,570,567,566,564,612,583,578,576,559,616,609,589,569,562,563,561,1454,597,587,573,619,620,618,617,608,600,598,599,591,585,580,581,579,584,568
base|Block de|-1|0|625,630,629,628,627,626
base|Pintura acrílica|-1|0|638,683,680,674,646,635,637,634,644,647,649,654,668,690,688,636,655,670,659,685,684,676,642,660,666,658,669,677,692,652,645,641,639,651,653,672,663,665,1452,662,675,682,681,686,679,691,687,678,689,693,650,643,648,640,657,664,661,656,671,667,673
base|Pintura textil|-1|0|714,703,705,698,695,697,694,704,702,700,701,706,699,696,707,709,1456,711,708,717,719,718,716,715,720,713,710,721,712
base|Témpera Pelikan|-1|0|725,724
base|Plastilina Pelikan|-1|0|746,748,754,753,751,747,749,755,752,750
base|Plastilina Crayola|-1|0|773,766,774,772,767,770,768,769,775,771
base|Bola de|-1|0|801,798,799,800
base|Listón 1|-1|0|824,821,818,819,828,825,827,826,820,831,822,829,830,823
base|Cordón de|-1|0|849,846,847,848,850,851
base|Pegamento blanco|-1|0|877,875,872,871,876,873,874
base|Juego de|-1|0|897,901,896,898,900,899
base|Mapa Carta|-1|0|931,926,929,925,922,924,923,928,930,927,941,1160,939,1154,937,935,938,936,940,1159,934,1155,1161,1157,932,1158,1156,933
base|Monografía Independencia|-1|0|950
base|Monografía La|-1|0|975,971,948,970
base|Lámina didáctica|-1|0|1011,1010,982,985,1004,991,1002,1009,986,993,998,987,997,1006,1007,1008,1005,989,990,1003,992,995,994,983,996,999,1001,984,1000,988
base|Estampas Frutas|-1|0|1025
base|Estampas Banderas|-1|0|1050
base|Clips Acco|-1|0|1077,1076,1074,1075
base|Notas adhesivas|-1|0|1103,1105,1104,1101,1100,1107,1106,1102
base|Cinta de|-1|0|1126,1121,1125,1127,1124,1123,1129,1128,1122,1132,1130,1131
base|Folder Oxford|-1|0|1152,1146,1145,1150,1139,1140,1142,1138,1144,1151,1149,1143,1148,1141,1147,1153
base|Carpeta de|-1|0|1188,1173,1175,1178,1179,1172,1170,1174,1182,1189,1177,1176,1171,1180,1181,1183,1184,1187,1185,1186
base|Cojín para|-1|0|1201,1200,1202
base|Lapicera Chenson|-1|0|1219,1222,1225,1218,1211,1209,1220,1206,1226,1223,1217,1215,1214,1210,1205,1208,1221,1224,1212,1213,1216,1204,1207,1203
base|Bolsa de|-1|0|1248,1257,1260,1253,1255,1252,1245,1243,1246,1244,1259,1258,1263,1254,1242,1247,1249,1261,1264,1265,1262,1251,1250,1256
base|Tarjeta de|-1|0|1277,1271,1276,1268,1269,1270,1267,1272,1273,1266,1275,1274
base|Globo Látex|-1|0|1294,1300,1301,1290,1289,1298,1285,1287,1286,1288,1295,1297,1303,1302,1299,1296,1284,1293,1292,1291
base|Vela de|-1|0|1335,1327,1322,1342,1343,1317,1314,1316,1319,1320,1324,1329,1326,1328,1330,1341,1339,1340,1344,1349,1352,1315,1321,1318,1325,1323,1333,1331,1332,1337,1334,1336,1338,1345,1346,1347,1348,1353,1350,1351
base|Cartucho de|-1|0|1386,1388,1378,1372,1375,1385,1384,1387,1389,1379,1381,1380,1383,1382,1373,1374,1377,1376
base|Enmicado Oficio|-1|0|1425
base|Colores Maped|-1|0|1450,211,210,209,1451,196,195,194
base|7508500638943|-1|0|0
base|\u00207508500638943\u0020|-1|0|0
base|7506806130659|-1|0|106
base|\u00207506806130659\u0020|-1|0|106
base|7509876720744|-1|0|211
base|\u00207509876720744\u0020|-1|0|211
base|7505160981778|-1|0|317
base|\u00207505160981778\u0020|-1|0|317
base|7505601780557|-1|0|473
base|\u00207505601780557\u0020|-1|0|473
base|7505167684689|-1|0|578
base|\u00207505167684689\u0020|-1|0|578
base|7508023439294|-1|0|678
base|\u00207508023439294\u0020|-1|0|678
base|P000064|-1|0|784
base|\u0020p000064\u0020|-1|0|784
base|7508471187006|-1|0|914
base|\u00207508471187006\u0020|-1|0|914
base|7501946809823|-1|0|1074
base|\u00207501946809823\u0020|-1|0|1074
base|7503989278710|-1|0|1178
base|\u00207503989278710\u0020|-1|0|1178
base|P000233|-1|0|1305
base|\u0020p000233\u0020|-1|0|1305
base|pluma punto fino|15|0|15,3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13
base|PLUMA PUNTO FINO|15|0|15,3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13
base|plu|15|0|15,3,28,22,1,4,11,0,37,2,24,39,10,25,63,8,9,16,18,20,26,188,51,29,280,67,6,17,14,21,191,54,33,5,7,279,1448,52,58,55,38,45,276,273,281,199,1451,196,192,60,62,68,65,64,49,35,40,47,46,19,12,13,27,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,197,198,195,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43,23,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277,189,190,194,193,161,157,159
rotulador|rotulador|-1|0|188:rotulador,280:rotulador,191:rotulador,279:rotulador,1448:rotulador,276:rotulador,273:rotulador,281:rotulador,199:rotulador,1451:rotulador,196:rotulador,192:rotulador,311:rotulador,293:rotulador,307:rotulador,301:rotulador,306:rotulador,297:rotulador,274:rotulador,289:rotulador,291:rotulador,282:rotulador,284:rotulador,285:rotulador,278:rotulador,288:rotulador,292:rotulador,197:rotulador,198:rotulador,195:rotulador,294:rotulador,296:rotulador,309:rotulador,302:rotulador,304:rotulador,303:rotulador,299:rotulador,300:rotulador,305:rotulador,298:rotulador,295:rotulador,310:rotulador,308:rotulador,312:rotulador,283:rotulador,287:rotulador,275:rotulador,290:rotulador,286:rotulador,277:rotulador,189:rotulador,190:rotulador,194:rotulador,193:rotulador
rotulador|rotul|-1|0|188:rotulador,280:rotulador,191:rotulador,279:rotulador,1448:rotulador,276:rotulador,273:rotulador,281:rotulador,199:rotulador,1451:rotulador,196:rotulador,192:rotulador,311:rotulador,293:rotulador,307:rotulador,301:rotulador,306:rotulador,297:rotulador,274:rotulador,289:rotulador,291:rotulador,282:rotulador,284:rotulador,285:rotulador,278:rotulador,288:rotulador,292:rotulador,197:rotulador,198:rotulador,195:rotulador,294:rotulador,296:rotulador,309:rotulador,302:rotulador,304:rotulador,303:rotulador,299:rotulador,300:rotulador,305:rotulador,298:rotulador,295:rotulador,310:rotulador,308:rotulador,312:rotulador,283:rotulador,287:rotulador,275:rotulador,290:rotulador,286:rotulador,277:rotulador,189:rotulador,190:rotulador,194:rotulador,193:rotulador
rotulador|plumon|-1|0|188,280,191,279,1448,276,273,281,199,1451,196,192,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,197,198,195,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277,189,190,194,193
rotulador|plumon rotulador|-1|0|188:rotulador,280:rotulador,191:rotulador,279:rotulador,1448:rotulador,276:rotulador,273:rotulador,281:rotulador,199:rotulador,1451:rotulador,196:rotulador,192:rotulador,311:rotulador,293:rotulador,307:rotulador,301:rotulador,306:rotulador,297:rotulador,274:rotulador,289:rotulador,291:rotulador,282:rotulador,284:rotulador,285:rotulador,278:rotulador,288:rotulador,292:rotulador,197:rotulador,198:rotulador,195:rotulador,294:rotulador,296:rotulador,309:rotulador,302:rotulador,304:rotulador,303:rotulador,299:rotulador,300:rotulador,305:rotulador,298:rotulador,295:rotulador,310:rotulador,308:rotulador,312:rotulador,283:rotulador,287:rotulador,275:rotulador,290:rotulador,286:rotulador,277:rotulador,189:rotulador,190:rotulador,194:rotulador,193:rotulador
rotulador|rotulador negro|-1|0|280:rotulador,300:rotulador
rotulador|pluma|-1|0|3,28,22,1,4,11,0,37,2,24,39,10,25,63,8,9,16,18,20,26,51,29,67,6,17,14,21,54,33,5,7,52,58,55,38,45,60,62,68,65,64,49,35,40,47,46,19,12,13,15,27,59,66,61,50,57,56,53,30,32,36,34,31,42,48,44,41,43,23,161,157,159
rotulador|rot|-1|0|
rotulador-solo|rotulador|-1|0|188:rotulador,280:rotulador,191:rotulador,279:rotulador,1448:rotulador,276:rotulador,273:rotulador,281:rotulador,199:rotulador,1451:rotulador,196:rotulador,192:rotulador,311:rotulador,293:rotulador,307:rotulador,301:rotulador,306:rotulador,297:rotulador,274:rotulador,289:rotulador,291:rotulador,282:rotulador,284:rotulador,285:rotulador,278:rotulador,288:rotulador,292:rotulador,197:rotulador,198:rotulador,195:rotulador,294:rotulador,296:rotulador,309:rotulador,302:rotulador,304:rotulador,303:rotulador,299:rotulador,300:rotulador,305:rotulador,298:rotulador,295:rotulador,310:rotulador,308:rotulador,312:rotulador,283:rotulador,287:rotulador,275:rotulador,290:rotulador,286:rotulador,277:rotulador,189:rotulador,190:rotulador,194:rotulador,193:rotulador
rotulador-solo|plumon|-1|0|188,280,191,279,1448,276,273,281,199,1451,196,192,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,197,198,195,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277,189,190,194,193
boligrafo|boligrafo punto fino|-1|0|3:boligrafo,1:boligrafo,4:boligrafo,11:boligrafo,0:boligrafo,2:boligrafo,10:boligrafo,8:boligrafo,9:boligrafo,16:boligrafo,18:boligrafo,6:boligrafo,17:boligrafo,14:boligrafo,5:boligrafo,7:boligrafo,19:boligrafo,12:boligrafo,13:boligrafo,15:boligrafo
boligrafo|bolig gel|-1|0|37:boligrafo,39:boligrafo,63:boligrafo,51:boligrafo,29:boligrafo,67:boligrafo,54:boligrafo,33:boligrafo,52:boligrafo,58:boligrafo,55:boligrafo,38:boligrafo,45:boligrafo,60:boligrafo,62:boligrafo,68:boligrafo,65:boligrafo,64:boligrafo,49:boligrafo,35:boligrafo,40:boligrafo,47:boligrafo,46:boligrafo,59:boligrafo,66:boligrafo,61:boligrafo,50:boligrafo,57:boligrafo,56:boligrafo,53:boligrafo,30:boligrafo,32:boligrafo,36:boligrafo,34:boligrafo,31:boligrafo,42:boligrafo,48:boligrafo,44:boligrafo,41:boligrafo,43:boligrafo
boligrafo|bol gel|-1|0|
boligrafo|pluma punto fino|-1|0|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15
boligrafo|boligrafo|-1|0|224,3:boligrafo,28:boligrafo,22:boligrafo,1:boligrafo,4:boligrafo,11:boligrafo,0:boligrafo,37:boligrafo,2:boligrafo,24:boligrafo,39:boligrafo,10:boligrafo,25:boligrafo,63:boligrafo,8:boligrafo,9:boligrafo,16:boligrafo,18:boligrafo,20:boligrafo,26:boligrafo,51:boligrafo,29:boligrafo,161:boligrafo,67:boligrafo,6:boligrafo,17:boligrafo,14:boligrafo,21:boligrafo,157:boligrafo,54:boligrafo,33:boligrafo,5:boligrafo,7:boligrafo,52:boligrafo,58:boligrafo,55:boligrafo,38:boligrafo,45:boligrafo,60:boligrafo,62:boligrafo,68:boligrafo,65:boligrafo,64:boligrafo,49:boligrafo,35:boligrafo,40:boligrafo,47:boligrafo,46:boligrafo,19:boligrafo,12:boligrafo,13:boligrafo,15:boligrafo,27:boligrafo,159:boligrafo,59:boligrafo,66:boligrafo,61:boligrafo,50:boligrafo,57:boligrafo,56:boligrafo,53:boligrafo,30:boligrafo,32:boligrafo,36:boligrafo,34:boligrafo,31:boligrafo,42:boligrafo,48:boligrafo,44:boligrafo,41:boligrafo,43:boligrafo,23:boligrafo
boligrafo|bolígrafo multicolor|-1|0|224
boligrafo|pluma pumto fino|-1|1|3,1,4,11,0,2,10,8,9,16,18,6,17,14,5,7,19,12,13,15,280,279,276,273,281,311,293,307,301,306,297,274,289,291,282,284,285,278,288,292,294,296,309,302,304,303,299,300,305,298,295,310,308,312,283,287,275,290,286,277
revueltos|boligrafo punto fino|-1|0|3:boligrafo,1:boligrafo,4:boligrafo,11:boligrafo,0:boligrafo,0:boligrafo,2:boligrafo,10:boligrafo,8:boligrafo,9:boligrafo,16:boligrafo,18:boligrafo,6:boligrafo,17:boligrafo,14:boligrafo,14:boligrafo,5:boligrafo,7:boligrafo,7:boligrafo,19:boligrafo,12:boligrafo,13:boligrafo,15:boligrafo
revueltos|bolig gel|-1|0|37:boligrafo,39:boligrafo,63:boligrafo,63:boligrafo,51:boligrafo,29:boligrafo,67:boligrafo,54:boligrafo,33:boligrafo,52:boligrafo,58:boligrafo,55:boligrafo,38:boligrafo,45:boligrafo,60:boligrafo,62:boligrafo,68:boligrafo,65:boligrafo,64:boligrafo,49:boligrafo,49:boligrafo,35:boligrafo,35:boligrafo,40:boligrafo,47:boligrafo,46:boligrafo,59:boligrafo,66:boligrafo,61:boligrafo,50:boligrafo,57:boligrafo,56:boligrafo,56:boligrafo,53:boligrafo,30:boligrafo,32:boligrafo,36:boligrafo,34:boligrafo,31:boligrafo,42:boligrafo,42:boligrafo,48:boligrafo,44:boligrafo,41:boligrafo,43:boligrafo
revueltos|pluma punto fino|-1|0|3,1,4,11,0,0,2,10,8,9,16,18,6,17,14,14,5,7,7,19,12,13,15
zanfona|zanfona|-1|0|17
zanfona|ocarina|-1|0|17:ocarina
engargolado|engar orden|-1|0|1458,1459
engargolado-vendido|engar orden|-1|0|1459,1458
"""

    /** Las familias de lo que encontró (hasta 30; con «200:», hasta 200): texto|huella de la agrupación. */
    val FAMILIAS: List<String> = listOf(FAMILIAS_1)

    private const val FAMILIAS_1 = """
a-z|e89b3d29
amarilla|51f1c9bf
ama|77e9329f
amrailla|51f1c9bf
aztecas|5246006e
azt|5246006e
azetcas|5246006e
blanca|6f0df659
bla|a0073436
blnaca|6f0df659
cr2032|ec58cf01
cr2|ec58cf01
cr0232|ec58cf01
carpeta|2d228141
car|b6b0d374
capreta|2d228141
clips|98aaa4b6
cli|98aaa4b6
clpis|98aaa4b6
corazones|e8897b2e
cor|1ebca0a4
coarzones|e8897b2e
cárdenas|52458c0f
cár|b6b0d374
cádrenas|52458c0f
duracell|f9b024c5
dur|68ca649c
duarcell|f9b024c5
escaneo|e8c25c95
esc|d014b95a
esacneo|e8c25c95
estándar|d2203719
est|ec1cf303
esátndar|d2203719
forro|1cf645b2
for|513464a5
graduación|7d43f258
gra|956e8f63
grdauación|7d43f258
héroes|5245f52b
hér|5245f52b
héores|5245f52b
jumbo|e88c482d
jum|e88c482d
jubmo|e88c482d
liso|5ebe585a
lis|48df4400
mapa|b6ab7331
map|d494a914
mayas|5246042f
may|5246042f
maays|5246042f
mini|a5c0740b
min|62efd62c
naranja|511342d6
nar|511342d6
naarnja|511342d6
números|eb27709c
núm|37d6d9f2
núemros|eb27709c
palitos|9972b3a8
pal|9972b3a8
pailtos|9972b3a8
pincel|3e93f1ab
pin|cbe1626a
picnel|3e93f1ab
plumones|383f43dd
plu|410d04d0
plmuones|383f43dd
puebla|5245f16a
pue|5245f16a
pubela|5245f16a
rojo|6d1fd1ab
roj|6d1fd1ab
signos|5247d5ab
sig|5247d5ab
singos|5247d5ab
swingline|100cf3f7
swi|100cf3f7
swnigline|100cf3f7
truper|e16bf201
tru|e16bf201
trpuer|e16bf201
verde|90b0f9ed
ver|e3b7bab0
vedre|90b0f9ed
adhesivas|f6e80905
adh|84d6ec52
adehsivas|f6e80905
canario|27834606
can|7b5948dd
caanrio|27834606
colorear|add9093d
col|523444a2
coolrear|add9093d
división|a743932d
div|a743932d
diivsión|a743932d
fluorescente|52237a2c
flu|52237a2c
flourescente|52237a2c
humano|52466d4b
hum|52466d4b
huamno|52466d4b
madera|9972b3a8
mad|2f8bfb12
maedra|9972b3a8
nombres|701433f2
nom|701433f2
nobmres|701433f2
permanente|205bd58f
per|d0ae3eeb
pemranente|205bd58f
regalo|a9f9ede1
reg|f5dbab04
reaglo|a9f9ede1
sobre|2f48b640
sob|2f48b640
sorbe|760338dc
Plu pun|eb073bed
pun Plu|eb073bed
Pluma Azul|3c49132c
7508|7545a77b
Plu gel|c9e37696
gel Plu|c9e37696
Pluma Café|d10e7a57
7509|43f13de6
Mar Sha|e2aa8820
Sha Mar|e2aa8820
Marcatextos Naranja|f31bd548
Mar par|dedcc297
par Mar|dedcc297
Marcador Verde|9048d197
7502|07085523
Plu col|383f43dd
col Plu|383f43dd
Plumones Caja|383f43dd
7501|2e87c26a
Láp col|e0872711
col Láp|e0872711
Lápiz Turquesa|f142ce86
7504|fa6e1799
Plumón limón|e12a1d3f
7503|1ca9e48b
Hoj col|3d40cbd2
col Hoj|3d40cbd2
Hojas Rojo|df849d7b
Pap chi|cb742ba0
chi Pap|cb742ba0
Papel Café|855c9779
Foa Pli|93d69629
Pli Foa|93d69629
Foamy Naranja|c0755090
Cua pro|203a1c91
pro Cua|203a1c91
Cuaderno juvenil|3f13ba32
Cua for|b35dfbaa
for Cua|b35dfbaa
Cuaderno Lisa|b68f90c0
7507|f70a94eb
Cua dib|e2c67053
dib Cua|e2c67053
Cuaderno Francés|04bc6e76
Pin acr|3c9f5570
acr Pin|3c9f5570
Pintura Beige|0ba24b15
7500|eb674117
Pin tex|5c58de28
tex Pin|5c58de28
Pintura Plateado|8328eba2
Pla Cra|50aab1cc
Cra Pla|50aab1cc
Plastilina Rosa|7029d49a
Lis Pla|2794c27c
Pla Lis|2794c27c
Listón Plateado|2794c27c
P000|a0d13226
Est Ome|6ac5792f
Ome Est|6ac5792f
Estambre Plateado|52384e48
7505|2efc1b99
Tij esc|b173a17e
esc Tij|b173a17e
Tijeras roma|e1a0d0c0
Mon Olm|524656c5
Olm Mon|524656c5
Monografía Olmecas|524656c5
Lám did|9611f67a
did Lám|9611f67a
Lámina México|8d8d218e
Lib par|7a93b856
par Lib|7a93b856
Libro Mandalas|e88b5bae
Not adh|11b39227
adh Not|11b39227
Notas Amarillo|f010bc83
Fol Oxf|aa0e1f61
Oxf Fol|aa0e1f61
Folder Naranja|ecf8dcbe
Coj par|588dc07f
par Coj|588dc07f
Cojín Negro|e8a52e4f
Bol reg|a9f9ede1
reg Bol|a9f9ede1
Bolsa Infantil|afd5ceca
Glo Lát|caeca7ed
Lát Glo|caeca7ed
Globo Rosa|085bcd57
Vel núm|55bd94f1
núm Vel|55bd94f1
Vela Rosa|ae129289
Cab USB|3d1df22a
USB Cab|3d1df22a
Cable USB|3d1df22a
Rec Mov|bacd529e
Mov Rec|bacd529e
Recarga Movistar|bacd529e
pluma gel|c9e37696
pl gel|c9e37696
cua pro|203a1c91
folder|5c882f37
lustre plata|51fd2ac7
negro|e32e9b16
surtido|164a1fa5
1mm|00000f80
sobre manial|2df2f4c9
cuadreno|0c8e7d23
pluma pumto fino|83978b79
Pluma  PUNTO fino|bad05437
lápiz|2727d430
z|3f450f2d
pluma punto fino|bad05437
lapicero|00d8f7e7
cuaderno|0c8e7d23
foami|7120ea1a
200:pluma gel|7a8c9456
200:pluma punto fino|bad05437
"""

    /** El producto que renombra CopiaAlDiaTests (su lugar) y cómo se llama después. */
    const val RENOMBRADO = 17

    const val NOMBRE_RENOMBRADO = "Pluma punto fino Pilot Zanfona"

    /** El que agrega Enter con «pluma gel» y con «pluma punto fino»: el elegido del primer renglón de sus familias. */
    const val PRIMERO_PLUMA_GEL = 37

    const val PRIMERO_PLUMA_PUNTO_FINO = 3

    /** Los productos (su lugar) en el orden de su nombre en el POS: «<» va después del anterior, «=» empata con él. */
    val ORDEN_DEL_CATALOGO: List<String> = listOf(ORDEN_DEL_CATALOGO_1)

    private const val ORDEN_DEL_CATALOGO_1 = """
<732 <733 <728 <729 <730 <731 <632 <633 <1407 <1406 <1409 <1408 <1403 <1402 <1405 <1404 <1110 <1111 <1108 <1109 <630
<629 <628 <627 <626 <625 <801 <798 <799 <800 <224 <1245 <1243 <1242 <1247 <1248 <1249 <1246 <1244 <1261 <1259 <1258
<1263 <1264 <1265 <1262 <1260 <1253 <1251 <1250 <1255 <1256 <1257 <1254 <1252 <1196 <1195 <1400 <1401 <1398 <1399
<1396 <1397 <1394 <1395 <1392 <1393 <1390 <1391 <918 <919 <920 <921 <1177 <1175 <1176 <1178 <1179 <1172 <1170 <1171
<1173 <1174 <1182 <1180 <1181 <1183 <1184 <1187 <1185 <1186 <1188 <1189 <1385 <1384 <1387 <1386 <1389 <1388 <1379
<1378 <1381 <1380 <1383 <1382 <1373 <1372 <1375 <1374 <1377 <1376 <346 <348 <345 <354 <360 <359 <358 <357 <355 <353
<351 <352 <350 <347 <356 <349 <1115 <1116 <1117 <1112 <1113 <1114 <1118 <1119 <1120 <1125 <1128 <1126 <1127 <1121
<1124 <1122 <1123 <1129 <1132 <1130 <1131 <1077 <1076 <1074 <1075 <1201 <1200 <1202 <200 <201 <202 <209 <210 =1450
<211 <206 <207 <208 <215 <216 <217 <212 =1447 <213 <214 =1443 <203 <204 <205 <905 <904 <903 <902 =1457 <907 <906
<1419 <1416 <1418 <1417 <847 <848 <849 <850 <846 <851 <163 <165 <164 <160 <159 <162 <161 <158 <157 <220 <221 =1449
<218 <219 <222 <223 =1445 <624 <623 <622 <621 <619 <620 <618 <613 <614 <612 <616 <617 <615 <610 <611 <609 <607 <608
<606 <601 <602 <600 <604 <605 <603 <598 <599 <597 <595 <596 <594 <589 <590 <588 <592 <593 <591 <586 <587 <585 <580
<581 <579 <583 <584 <582 <577 <578 <576 <571 <572 <570 <574 <575 <573 <568 <569 <567 <562 <563 <561 <565 <566 <564
<559 <560 <558 =1454 <631 <521 <519 <520 <518 <525 <523 <524 <522 <529 <527 <528 <526 <533 <531 <532 <530 <513 <511
<512 <510 <517 <515 <516 <514 <505 <503 <504 <502 <509 <507 <508 <506 <553 <551 <552 <550 <557 <555 <556 <554 <545
<543 <544 <542 <549 <547 <548 <546 <537 <535 <536 <534 <541 <539 <540 <538 <489 <487 <488 <486 <493 <491 <492 <490
<497 <495 =1446 <496 <494 <501 <499 <500 <498 <481 <479 <480 <478 <485 <483 <484 <482 <473 <471 <472 <470 <477 <475
<476 <474 <784 <785 <786 <787 <779 <776 <782 <777 <778 <781 <783 <780 <1410 <1411 <1422 <1421 <1420 <1089 <1090
<1088 <1086 <1087 <1085 <1083 <1084 <1082 <1424 <1423 <1425 <1426 <853 <855 <867 <852 <861 <863 <862 <866 <860 <858
<859 <864 <857 <854 <865 <856 <1033 <1015 <1023 <1050 <1037 <1029 <1014 <1043 <1012 <1040 <1041 <1045 <1032 <1016
<1026 <1035 <1024 <1013 <1034 <1020 <1038 <1025 <1019 <1048 <1031 <1039 <1027 <1021 <1042 <1044 <1030 <1028 <1047
<1051 <1036 <1017 <1049 <1046 <1018 <1022 <1069 <1068 <1067 <413 <415 <412 <421 <423 <422 <420 <418 <419 <424 <417
<414 <425 <416 <441 <443 <440 <449 <448 <446 <447 <445 <442 <444 <427 <429 <426 <435 <437 <436 <434 <432 <433 <438
<431 <428 <439 <430 <1158 <1155 <1154 <1161 <1160 <1156 <1159 <1157 <1166 <1163 <1162 <1169 <1168 <1164 <1167 <1165
<1142 <1139 <1138 <1145 <1144 <1140 <1143 <1141 <1150 <1147 <1146 <1153 <1152 <1148 <1151 <1149 <451 <452 <450 <454
<455 <453 <457 <456 <1072 <1073 <1295 <1297 <1294 <1303 <1302 <1300 <1301 <1299 <1296 <1298 <1285 <1287 <1284 <1293
<1292 <1290 <1291 <1289 <1286 <1288 <1305 <1307 <1304 <1313 <1312 <1310 <1311 <1309 <1306 <1308 <168 <169 <170 <171
<166 <167 <174 <175 <172 <173 <1080 <1081 <1078 <1079 <317 <318 <319 <320 <315 <316 <313 <314 <331 <334 <338 <339
<337 <335 <336 <332 <333 <340 <321 <324 <328 <329 <327 <325 <326 <322 <323 <330 <1414 <1415 <1412 <1413 <900 <901
<898 <899 <896 <897 <982 <1005 <989 <990 <1010 <986 <993 <1003 <992 <995 <985 <998 <987 <1004 <1011 <997 <994 <983
<991 <1002 <996 <1006 <1007 <1008 <999 <1001 <984 <1000 <988 <1009 <1220 <1226 <1225 <1223 <1221 <1219 <1222 <1224
<1212 <1218 <1217 <1215 <1213 <1211 <1214 <1216 <1204 <1210 <1209 <1207 <1205 <1203 <1206 <1208 <884 <885 <886 <881
<882 <883 <878 <879 <880 <252 <253 <262 <261 <263 <249 <268 <272 <271 <251 <260 <258 <254 <250 <270 <255 <256 <257
<269 <264 <267 <266 <265 <259 <228 <229 <238 <237 <239 <225 <244 <248 <247 <227 <236 <234 <230 <226 <246 <231 <232
<233 <245 <240 <243 <242 <241 <235 <71 <72 <75 <76 <69 <70 <73 <74 <77 <78 <1063 <1052 <1055 <1053 <1060 <1058 <1056
<1066 <1062 <1064 <1059 <1054 <1057 <1061 <1065 <1197 <1198 <1199 <794 <805 <807 <804 <813 <815 <814 <812 <810 <811
<816 <809 <806 <817 <808 <819 <821 <818 <827 <829 <828 <826 <824 <825 <830 <823 <820 <831 <822 <833 <835 <832 <841
<843 <842 <840 <838 <839 <844 <837 <834 <845 <836 <1239 <1240 <1241 <929 <925 <928 <927 <924 <922 <923 <931 <926
<930 <939 <935 <938 <937 <934 <932 <933 <941 <936 <940 <149 <150 <151 <152 <153 <154 <155 <156 <141 <142 <143 <144
<145 <146 <147 <148 <137 <138 <139 <140 <133 <134 <135 <136 <129 <130 <131 <132 <125 <126 <127 <128 <109 <113 <114
<112 <116 <115 <111 <110 <101 <105 <106 <104 <108 <107 <103 <102 <93 <97 <98 <96 <100 <99 <95 <94 <117 <121 <122
<120 =1455 <124 <123 <119 <118 <1369 <1370 <1371 <1366 <1367 <1368 <89 =1444 <87 <88 <92 <90 <91 <1227 <1232 <1231
<1230 <1228 <1229 <1233 <1238 <1237 <1236 <1234 <1235 <973 <972 <963 <961 <962 <958 <954 <942 <977 <969 <956 <980
<966 <957 <976 <946 <965 <970 <979 <953 <947 <950 <944 <945 <971 <975 <948 <967 <959 <981 <943 <955 <960 <974 <952
<951 <978 <964 <968 <949 <1279 <1280 <1281 <1278 <1282 <1283 <1105 <1104 <1107 <1106 <1101 <1100 <1103 <1102 <868
<870 <869 <795 <797 <796 <404 <406 <403 <409 <410 <408 <405 <407 <1357 <1356 <1355 <1354 <376 <378 <375 <384 <386
<385 <383 <381 <382 <387 <380 <377 <388 <379 <362 <364 <361 <370 <372 <371 <369 <367 <368 <373 <366 <363 <374 <365
<463 <461 <462 <460 <458 <459 <411 <390 <392 <389 <398 <400 <399 <397 <395 <396 <401 <394 <391 <402 <393 <341 <342
<343 <344 <466 <464 <469 <468 <467 <465 <871 <876 <877 <875 <873 <874 <872 <1091 <1092 <1093 <1097 <1098 <1099 <1094
<1095 <1096 <1360 =1453 <1358 <1359 <1361 <1364 <1362 <1363 <1365 <744 <745 <740 <741 <742 <743 <738 <739 <734 <735
<736 <737 <675 <677 <690 <692 <674 <683 <685 <684 <688 <682 <680 <681 <686 <679 <676 <691 <687 <678 <689 <693 <635
<637 <650 <652 <634 <643 <645 <644 <648 <642 <640 <641 <646 <639 <636 <651 <647 <638 <649 <653 <655 <657 <670 <672
<654 <663 <665 <664 =1452 <668 <662 <660 <661 <666 <659 <656 <671 <667 <658 <669 <673 <695 <697 <694 <703 <705 <704
<702 <700 <701 <706 <699 <696 <707 <698 <709 =1456 <711 <708 <717 <719 <718 <716 <714 <715 <720 <713 <710 <721 <712
<790 <791 <792 <793 <1070 <1071 <802 <803 <767 <769 <766 <775 <774 <772 <773 <771 <768 <770 <747 <749 <746 <755 <754
<752 <753 <751 <748 <750 <757 <759 <756 <765 <764 <762 <763 <761 <758 <760 <60 <62 <59 <68 <67 <65 <66 <64 <61 <63
<50 <52 <49 <58 <57 <55 <56 <54 <51 <53 <30 <32 <29 <38 <37 <35 <36 <34 <31 <33 <40 <42 <39 <48 <47 <45 <46 <44 <41
<43 <0 <1 <2 <3 <8 <9 <10 <11 <4 <5 <6 <7 <16 <17 <18 <19 <12 <13 <14 <15 <20 <21 <22 <23 <24 <25 <26 <27 <28 <294
<296 <309 <311 <293 <302 <304 <303 <307 <301 <299 <300 <305 <298 <295 <310 <306 <297 <308 <312 <274 <276 <289 <291
<273 <282 <284 <283 <287 <281 <279 <280 <285 <278 <275 <290 <286 <277 <288 <292 <197 <198 <199 <188 <189 <190 <194
=1451 <195 <196 <191 =1448 <192 <193 <83 <84 <85 <86 <79 <80 <81 <82 <1190 <1191 <1437 <1435 <1438 <1436 <1441 <1439
<1442 <1440 <1433 <1431 <1434 <1432 <893 <894 <895 <890 <891 <892 <887 <888 <889 <183 <184 <182 <177 <178 <176 <180
<181 <179 <186 <187 <185 <1193 <1192 <1194 <788 <789 <1136 <1135 <1137 <1133 <1134 <1267 <1276 <1272 <1273 <1266
<1268 <1269 <1275 <1271 <1277 <1270 <1274 <727 <726 <725 <724 <723 <722 <915 <914 <913 <912 <917 <916 <1428 <1429
<1427 <1430 <910 <911 <908 <909 <1317 <1314 <1315 <1316 <1321 <1318 <1319 <1320 <1325 <1322 <1323 <1324 <1329 <1326
<1327 <1328 <1333 <1330 <1331 <1332 <1337 <1334 <1335 <1336 <1341 <1338 <1339 <1340 <1345 <1342 <1343 <1344 <1349
<1346 <1347 <1348 <1353 <1350 <1351 <1352
"""

    /** Nombres raros en el orden del POS: «<» va después del anterior, «=» empata con él. */
    val CASOS_DE_ORDEN: List<String> = listOf(CASOS_DE_ORDEN_1)

    private const val CASOS_DE_ORDEN_1 = """
<
<\u0020
<\u0020\u0020
<\u0020casa
<_casa
<-1
<-casa
<,casa
<;casa
<:casa
<!casa
<¡casa
<?casa
<¿casa
<.casa
<·casa
<'casa
<\u0022casa
<«casa
<»casa
<(casa
<)casa
<[casa
<]casa
<{casa
<}casa
<§ 2
<¶ 2
<@casa
<*casa
</casa
<\u005ccasa
<&casa
<#casa
<%casa
<`casa
<´casa
<^casa
<¯casa
<¨casa
<¸casa
<°casa
<© marca
<® marca
<+1
<+casa
<±casa
<÷casa
<×casa
<<casa
<=casa
<>casa
<¬casa
<\u007ccasa
<~casa
<¤casa
<¢casa
<\u0024casa
<£casa
<¥casa
<€casa
<0casa
<1
<1 5
<1,5
<1.5
<½
<⅓
<¼
<10
<100
<¹casa
<2
=²
<20
<²casa
<¾
<³casa
<9casa
<A
=a
<á
<à
<ă
<â
<ǎ
<å
<ä
<ã
<ą
<ā
<acasa
=ªcasa
<ae
<æ
<Aether
<Æther
<Angel
<Ángel
<Arbol
=arbol
<ÁRBOL
=Árbol
=árbol
<Boligrafo
<Bolígrafo
<Bolígrafo multicolor
<Bolsa \u002410
<Bolsa €10
<Bolsa 10%
<Bolsa 10\u0024
<Cafe
<CAFÉ
=Café
=cafe\u0301
<Cafes
<Cafés
<CASA
=Casa
=casa
<Cedilla
<Çedilla
<Cinta Canela
<Cinta`  Canela^
<Cuad. Prof
<Cuaderno
<Cuadérno
<CUADERNO PROF 100H
=Cuaderno Prof 100h
<Cuaderno Prof. 100h
<Cuaderno Profesional
<Cuadernos
<e\u0301
=é
<è
<ê
<ë
<Engargolado orden alfa
<Engargolado orden beta
<Etiqueta #9
<Etiqueta N° 9
<Etiqueta Nº 9
=Etiqueta № 9
<Etiqueta No. 9
<Exito
<Éxito
<fino
=ﬁno
<flor
=ﬂor
<Foami 45×60
<Foami 45X60
=Foami 45x60
<Guero
<Güero
<Hoja ¡carta!
<Hoja ¿carta?
<Hoja 'carta'
<Hoja ‘carta’
<Hoja \u0022carta\u0022
<Hoja “carta”
<Hoja «carta»
<Hoja A4
=Hoja a4
<I
=i
<İ
<II
=Ⅱ
<Íntimo
<Ìtalo
<Item
<ītem
<ı
<Jgo Lapiceros C/10 Pluvia
<Jgo Lapiceros C/4 Pluvia
<Lapicero 0.5mm Negro P/Fino Pluvia
<Lapicero 0.7mm Azul P/Fino Pluvia
<Lapicero 0.7mm Negro P/Fino Pluvia
<Lapicero 1.0 Mm Negro Trazo
<Lapicero 1.0mm Negro P/Mediano Pluvia
<Lapicero 4 Puntas Trazo
<Lapices
<Lápices
<LAPIZ
=Lapiz
<Lapíz
<Lápiz
<Lapiz Azul
<Lapiz¨ Azul
<Mu´ñeca
<Muñeca
<N
=n
<Nanda
<Nandu
<Nz
=nz
<N\u0303
=n\u0303
=Ñ
=ñ
<ña
<Ñame
<Ñandú
<o
<ö
<ø
<ºcasa
<oe
<œ
<Oeuvre
<Œuvre
<Olsen
<Ølsen
<Oso
<Óvalo
<PAPEL
=Papel
=papel
<Papel  lustre
<Papel (lustre)
<Papel #2
<Papel 1/2
<Papel 1⁄2
=Papel ½
<Papel ¼
<Papel 10
<Papel 2
<Papel lustre
<Papel_lustre
<Papel-lustre
<Papel,lustre
<Papel.lustre
<Papel/lustre
<Papelería
<Pinguino
<Pingüino
<Pluma\u0009Azul
<Pluma - Azul
<Pluma – Azul
<Pluma — Azul
<Pluma ... Azul
=Pluma … Azul
<Pluma · Azul
<Pluma Azul
=Pluma\u00a0Azul
<Pluma p. fino Bic Azul · Fino
<PLUMA PUNTO FINO BIC AZUL · FINO
=Pluma punto fino Bic Azul · Fino
<PlumaAzul
=Pluma\u00adAzul
<Plumas
<Plumín
<Plumon
<Plumón
<Plumones
<Regla 30 cm
<Regla 30-cm
<Regla 30cm
<SS
=ss
<ß
=ẞ
<Strasse
<Straße
<TM marca
=™ marca
<Tubo ⌀ 1/2
<Ümlaut
<Úrsula
<Zcasa
=zcasa
<ZZ
=Zz
=zZ
=zz
<µcasa
"""

    /** Nombres raros, lo que lee el POS de cada uno (texto/eje/clave) y el orden de sus medidas. */
    val LECTOR: List<String> = listOf(LECTOR_1)

    private const val LECTOR_1 = """
Lapicero 1.2345mm Negro|Lapicero//LAPICERO 1.2345mm/Medida/1.235MM Negro/Color/NEGRO|1.235
Lapicero 0.0005mm Negro|Lapicero//LAPICERO 0.0005mm/Medida/0.001MM Negro/Color/NEGRO|0.001
Lapicero 0.0015mm Negro|Lapicero//LAPICERO 0.0015mm/Medida/0.002MM Negro/Color/NEGRO|0.002
Lapicero 2.5 mm Azul|Lapicero//LAPICERO 2.5/Medida/2.5MM mm/Medida/ Azul/Color/AZUL|2.5
Lapicero 1,5 cm Rojo|Lapicero//LAPICERO 1,5/Medida/1.5CM cm/Medida/ Rojo/Color/ROJO|1.5
Lapicero .5 Negro|Lapicero//LAPICERO .5/Medida/0.5MM Negro/Color/NEGRO|0.5
Lapicero 1.0 Negro Trazo|Lapicero//LAPICERO 1.0/Medida/1MM Negro/Color/NEGRO Trazo//TRAZO|1
Lapicero 007mm|Lapicero//LAPICERO 007mm/Medida/7MM|7
Foami .5x60|Foami//FOAMI .5x60/Medida/0.5X60|0.5
Foami 45x60cm Azul|Foami//FOAMI 45x60cm/Medida/45X60CM Azul/Color/AZUL|45
Foami 45X60 Mts|Foami//FOAMI 45X60/Medida/45X60 Mts//MTS|45
Foami 45x60x2|Foami//FOAMI 45x60x2//45X60X2|
Bola Unicel No 9|Bola//BOLA Unicel/Marca/UNICEL No/Medida/#9 9/Medida/|9
Bola Unicel Num 10|Bola//BOLA Unicel/Marca/UNICEL Num/Medida/#10 10/Medida/|10
Bola Unicel N°9|Bola//BOLA Unicel/Marca/UNICEL N°9/Medida/#9|9
Bola Unicel No.12|Bola//BOLA Unicel/Marca/UNICEL No.12/Medida/#12|12
Bola Unicel #7|Bola//BOLA Unicel/Marca/UNICEL #7/Medida/#7|7
Cuaderno C7 Escriba|Cuaderno//CUADERNO C7/Rayado/C7 Escriba//ESCRIBA|
Cuaderno Doble Raya Escriba|Cuaderno//CUADERNO Doble/Rayado/DOBLE RAYA Raya/Rayado/ Escriba//ESCRIBA|
Cuaderno Cuadro Alemán Norma|Cuaderno//CUADERNO Cuadro/Rayado/CUADRO ALEMAN Alemán/Rayado/ Norma/Marca/NORMA|
Cuaderno Cuadro Chico|Cuaderno//CUADERNO Cuadro/Rayado/CUADRO CHICO Chico/Rayado/|
Cuaderno Cuadricula|Cuaderno//CUADERNO Cuadricula/Rayado/CUADRO|
Cuaderno Blanco|Cuaderno//CUADERNO Blanco/Rayado/BLANCO|
Libreta Rayada Mini|Libreta//LIBRETA Rayada/Rayado/RAYA Mini/Medida/MINI|1
Hojas Blancas 100 pzs|Hojas//HOJAS Blancas/Color/BLANCO 100/Contenido/C/100 pzs/Contenido/|
Paquete 10pzas Clips|Paquete//PAQUETE 10pzas/Contenido/C/10 Clips//CLIPS|
Caja Clips C/12|Caja//CAJA Clips//CLIPS C/12/Contenido/C/12|
Clips 12 Piezas|Clips//CLIPS 12/Contenido/C/12 Piezas/Contenido/|
Cartulina Doble Carta Blanca|Cartulina//CARTULINA Doble/Medida/DOBLE CARTA Carta/Medida/ Blanca/Color/BLANCO|13
Papel 0/0|Papel//PAPEL 0/0/Medida/0/0|NaN
Papel 1/0|Papel//PAPEL 1/0/Medida/1/0|Infinity
Papel 3/4 Azul|Papel//PAPEL 3/4/Medida/3/4 Azul/Color/AZUL|0.75
Liston Palo De Rosa|Liston//LISTON Palo/Color/PALO DE ROSA De/Color/ Rosa/Color/|
Liston Palo de rosa 1/2|Liston//LISTON Palo/Color/PALO DE ROSA de/Color/ rosa/Color/ 1/2/Medida/1/2|0.5
Pluma V Colores|Pluma//PLUMA V/Color/SURTIDO Colores/Color/|
Pluma C/V Colores|Pluma//PLUMA C/V/Color/SURTIDO Colores/Color/|
Pluma Colores Surt.|Pluma//PLUMA Colores/Color/SURTIDO Surt./Color/|
Pluma Surtido Surtidos Surt|Pluma//PLUMA Surtido/Color/SURTIDO Surtidos/Color/ Surt/Color/|
Pluma Multicolor Bic|Pluma//PLUMA Multicolor/Color/SURTIDO Bic/Marca/BIC|
Pluma Azul Marino Bic|Pluma//PLUMA Azul/Color/AZUL MARINO Marino/Color/ Bic/Marca/BIC|
Pluma azul rey Bic|Pluma//PLUMA azul/Color/AZUL REY rey/Color/ Bic/Marca/BIC|
Pluma Rosa Mexicano Pelikan|Pluma//PLUMA Rosa/Color/ROSA MEXICANO Mexicano/Color/ Pelikan/Marca/PELIKAN|
Pluma Verde Limón Bic|Pluma//PLUMA Verde/Color/VERDE LIMON Limón/Color/ Bic/Marca/BIC|
Pluma Negra Fina Bic|Pluma//PLUMA Negra/Color/NEGRO Fina//FINA Bic/Marca/BIC|
Pintura 250 Ml Negra Trazo|Pintura//PINTURA 250/Medida/250ML Ml/Medida/ Negra/Color/NEGRO Trazo//TRAZO|250
Pintura 1 Lt Blanca|Pintura//PINTURA 1/Medida/1L Lt/Medida/ Blanca/Color/BLANCO|1
Pintura 1 L|Pintura//PINTURA 1/Medida/1L L/Medida/|1
Resistol 850 1 kg|Resistol//RESISTOL 850//850 1/Medida/1KG kg/Medida/|1
Hilo 100 m|Hilo//HILO 100/Medida/100M m/Medida/|100
Hilo 100 mts Rojo|Hilo//HILO 100/Medida/100M mts/Medida/ Rojo/Color/ROJO|100
Cinta 3/4\u0022 Scotch|Cinta//CINTA 3/4\u0022//3/4\u0022 Scotch/Marca/SCOTCH|
Cinta 18mm x 33m|Cinta//CINTA 18mm/Medida/18MM x// 33m/Medida/33M|18,33
Cinta 24x50 Canela|Cinta//CINTA 24x50/Medida/24X50 Canela//CANELA|24
Tijeras 5\u0022 Maped|Tijeras//TIJERAS 5\u0022//5\u0022 Maped/Marca/MAPED|
Papel Lustre Plateado|Papel//PAPEL Lustre//LUSTRE Plateado/Color/PLATA|
Papel lustre plata|Papel//PAPEL lustre//LUSTRE plata/Color/PLATA|
Sobre Manila Carta|Sobre//SOBRE Manila//MANILA Carta/Medida/CARTA|11
Sobre manila oficio|Sobre//SOBRE manila//MANILA oficio/Medida/OFICIO|12
Sobre  Manila   Doble Carta|Sobre//SOBRE Manila//MANILA Doble/Medida/DOBLE CARTA Carta/Medida/|13
Hoja A4 Chamex|Hoja//HOJA A4/Medida/A4 Chamex/Marca/CHAMEX|11.5
Folder Tamaño Carta Azul|Folder//FOLDER Tamaño//TAMAÑO Carta/Medida/CARTA Azul/Color/AZUL|11
Lapiz Adhesivo 21 G Pritt|Lapiz//LAPIZ Adhesivo//ADHESIVO 21/Medida/21G G/Medida/ Pritt/Marca/PRITT|21
Lápiz Mirado 2 HB|Lápiz//LAPIZ Mirado//MIRADO 2//2 HB//HB|
Lápiz Mirado No 2|Lápiz//LAPIZ Mirado/Marca/MIRADO No/Medida/#2 2/Medida/|2
Marcador P/Fino Negro Sharpie|Marcador//MARCADOR P/Fino//P/FINO Negro/Color/NEGRO Sharpie/Marca/SHARPIE|
Marcador 0.4mm P/Fino Negro|Marcador//MARCADOR 0.4mm/Medida/0.4MM P/Fino/Medida/ Negro/Color/NEGRO|0.4
Plumon 1.0mm P/Mediano Negro P/Medio|Plumon//PLUMON 1.0mm/Medida/1MM P/Mediano/Medida/ Negro/Color/NEGRO P/Medio/Medida/|1
Engargolado orden alfa|Engargolado//ENGARGOLADO orden//ORDEN alfa//ALFA|
Jgo Geometria 4 Pz Maped|Jgo//JGO Geometria//GEOMETRIA 4/Contenido/C/4 Pz/Contenido/ Maped/Marca/MAPED|
Jgo Geometria 4 Pzs|Jgo//JGO Geometria//GEOMETRIA 4/Contenido/C/4 Pzs/Contenido/|
Jgo de Lapices C/12 Prismacolor|Jgo//JGO de// Lapices//LAPICES C/12/Contenido/C/12 Prismacolor/Marca/PRISMACOLOR|
Juego Colores 24 Piezas Prismacolor|Juego//JUEGO Colores/Color/SURTIDO 24/Contenido/C/24 Piezas/Contenido/ Prismacolor/Marca/PRISMACOLOR|
Plastilina Barra 180 Gr Vinci|Plastilina//PLASTILINA Barra//BARRA 180/Medida/180G Gr/Medida/ Vinci/Marca/VINCI|180
Plastilina 1 Kg|Plastilina//PLASTILINA 1/Medida/1KG Kg/Medida/|1
Papel Kraft 1 Mt|Papel//PAPEL Kraft/Marca/KRAFT 1/Medida/1M Mt/Medida/|1
Cartulina Fluorescente 50x70|Cartulina//CARTULINA Fluorescente//FLUORESCENTE 50x70/Medida/50X70|50
Papel China 50x70 Rosa|Papel//PAPEL China//CHINA 50x70/Medida/50X70 Rosa/Color/ROSA|50
Mu´ñeca Grande|Mu´ñeca//MUÑECA Grande/Medida/GRANDE|4
Lapiz¨ Azul|Lapiz¨//LAPIZ Azul/Color/AZUL|
Cinta`  Canela^|Cinta`//CINTA Canela^//CANELA|
Engrapadora Pegaso Paper Mate|Engrapadora//ENGRAPADORA Pegaso/Marca/PEGASO Paper/Marca/PAPER MATE Mate/Marca/|
Pluma Kilométrico Paper Mate Azul|Pluma//PLUMA Kilométrico//KILOMETRICO Paper/Marca/PAPER MATE Mate/Marca/ Azul/Color/AZUL|
Cuaderno Prof 100h Raya Escriba|Cuaderno//CUADERNO Prof//PROF 100h/Medida/100H Raya/Rayado/RAYA Escriba//ESCRIBA|100
Cuaderno Prof 100 Hojas Raya|Cuaderno//CUADERNO Prof//PROF 100/Medida/100H Hojas/Medida/ Raya/Rayado/RAYA|100
Block Esquela Cuadro|Block//BLOCK Esquela/Medida/ESQUELA Cuadro/Rayado/CUADRO|10
CUADERNO PROF 100H RAYA ESCRIBA|CUADERNO//CUADERNO PROF//PROF 100H/Medida/100H RAYA/Rayado/RAYA ESCRIBA//ESCRIBA|100
Cuad. Prof 100h Raya Escriba|Cuad.//CUAD Prof//PROF 100h/Medida/100H Raya//RAYA Escriba//ESCRIBA|100
Pluma p. fino Bic Azul · Fino|Pluma//PLUMA p.//P fino//FINO Bic//BIC Azul/Color/AZUL ·//· Fino//FINO|
Pluma de gel Pentel Negro · 0.7 mm|Pluma//PLUMA de// gel//GEL Pentel//PENTEL Negro/Color/NEGRO ·//· 0.7/Medida/0.7MM mm/Medida/|0.7
Pluma de gel Pentel Negro · 0.7mm|Pluma//PLUMA de// gel//GEL Pentel//PENTEL Negro/Color/NEGRO ·//· 0.7mm/Medida/0.7MM|0.7
Pluma de gel Pentel Negro 0.7|Pluma//PLUMA de// gel//GEL Pentel/Marca/PENTEL Negro/Color/NEGRO 0.7/Medida/0.7MM|0.7
\u0020Lapicero   azul \u0020|Lapicero//LAPICERO azul/Color/AZUL|
Lapicero\u0009Azul|Lapicero\u0009Azul//LAPICERO AZUL|
Lapicero\u00a0Azul|Lapicero\u00a0Azul//LAPICERO AZUL|
Papel Ñandú|Papel//PAPEL Ñandú//ÑANDU|
Niño Dios Chico|Niño//NIÑO Dios//DIOS Chico/Medida/CHICO|2
Ñ|Ñ//Ñ|
x|x//X|
a b c|a//A b//B c//C|
De Del Y O X|De//DE Del// Y// O// X//|
Pluma Bic Bic|Pluma//PLUMA Bic/Marca/BIC Bic/Marca/BIC|
Pluma Azul Azul Bic|Pluma//PLUMA Azul/Color/AZUL Azul/Color/AZUL Bic/Marca/BIC|
Colores Surt.|Colores//COLORES Surt./Color/SURTIDO|
Lapiz Bicolor Rojo Azul|Lapiz//LAPIZ Bicolor//BICOLOR Rojo/Color/ROJO Azul/Color/AZUL|
Lapiz Bicolor Rojo/Azul|Lapiz//LAPIZ Bicolor//BICOLOR Rojo/Azul//ROJO/AZUL|
"""

}
