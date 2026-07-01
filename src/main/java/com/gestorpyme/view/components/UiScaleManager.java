package com.gestorpyme.view.components;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestor de ESCALA (zoom) visual global de la aplicacion.
 *
 * Permite que el usuario aumente o reduzca el tamano de toda la interfaz
 * (fuentes de etiquetas, botones, campos, combos, tablas y encabezados) sin
 * modificar el layout. El valor se mantiene EN MEMORIA durante la sesion
 * (no requiere cambios en la base de datos para esta entrega).
 *
 * Estrategia:
 * 1. Se capturan una sola vez las fuentes base del Look&Feel.
 * 2. Al cambiar la escala se reescalan esas fuentes en el {@link UIManager}.
 * 3. Se refresca el arbol de componentes de las ventanas registradas con
 *    {@link SwingUtilities#updateComponentTreeUI(java.awt.Component)}.
 *
 * Ademas, {@link UiTheme} multiplica sus tamanos de fuente por esta escala, de
 * modo que los componentes con fuente asignada explicitamente tambien crecen al
 * reconstruirse (p. ej. al volver a entrar a un modulo del Dashboard).
 *
 * Clase de utilidades estaticas.
 */
public final class UiScaleManager {

    /** Escala minima permitida (90%). */
    public static final double MIN = 0.90;
    /** Escala maxima permitida (150%). */
    public static final double MAX = 1.50;

    /** Opciones sugeridas para mostrar en un selector. */
    public static final double[] OPCIONES = {0.90, 1.00, 1.10, 1.25, 1.50};

    /** Claves de fuente del Look&Feel que se reescalan. */
    private static final String[] CLAVES_FUENTE = {
            "Label.font", "Button.font", "ToggleButton.font", "RadioButton.font", "CheckBox.font",
            "ComboBox.font", "TextField.font", "FormattedTextField.font", "PasswordField.font",
            "TextArea.font", "TextPane.font", "EditorPane.font", "List.font", "Table.font",
            "TableHeader.font", "TabbedPane.font", "TitledBorder.font", "Spinner.font",
            "Menu.font", "MenuItem.font", "MenuBar.font", "OptionPane.font", "ToolTip.font",
            "Panel.font", "ScrollPane.font", "Viewport.font"
    };

    private static final Map<String, Font> FUENTES_BASE = new LinkedHashMap<>();
    private static final List<Window> VENTANAS = new ArrayList<>();
    private static double escala = 1.0;
    private static boolean baseCapturada = false;

    private UiScaleManager() {
    }

    /** @return la escala actual (1.0 == 100%). */
    public static double getEscala() {
        return escala;
    }

    /**
     * Registra una ventana para que se refresque cuando cambie la escala.
     * Es seguro llamarlo varias veces; no se registra dos veces la misma ventana.
     *
     * @param ventana ventana de nivel superior (JFrame/JDialog).
     */
    public static void registrar(Window ventana) {
        if (ventana != null && !VENTANAS.contains(ventana)) {
            VENTANAS.add(ventana);
        }
    }

    /** Captura por unica vez las fuentes base del Look&Feel actual. */
    private static void capturarBaseSiHaceFalta() {
        if (baseCapturada) {
            return;
        }
        for (String clave : CLAVES_FUENTE) {
            Font f = UIManager.getFont(clave);
            if (f != null) {
                // Se guarda una copia "limpia" del tamano original.
                FUENTES_BASE.put(clave, new Font(f.getName(), f.getStyle(), f.getSize()));
            }
        }
        baseCapturada = true;
    }

    /**
     * Aplica una nueva escala (se ajusta al rango permitido) y refresca las
     * ventanas registradas.
     *
     * @param nuevaEscala factor deseado (p. ej. 1.25 para 125%).
     */
    public static void aplicarEscala(double nuevaEscala) {
        capturarBaseSiHaceFalta();
        escala = Math.max(MIN, Math.min(MAX, nuevaEscala));

        for (Map.Entry<String, Font> e : FUENTES_BASE.entrySet()) {
            Font base = e.getValue();
            int tam = Math.max(9, Math.round(base.getSize() * (float) escala));
            UIManager.put(e.getKey(), new FontUIResource(base.getName(), base.getStyle(), tam));
        }

        // Refresca las ventanas vivas (se descartan las que ya no existen).
        VENTANAS.removeIf(w -> !w.isDisplayable());
        for (Window w : new ArrayList<>(VENTANAS)) {
            SwingUtilities.updateComponentTreeUI(w);
            w.validate();
            w.repaint();
        }
    }

    /**
     * Escala un tamano base segun la escala actual (para fuentes/altos calculados
     * manualmente, p. ej. en {@link UiTheme} o {@link TableUtils}).
     *
     * @param valorBase tamano base.
     * @return el tamano escalado (minimo 1).
     */
    public static int escalar(int valorBase) {
        return Math.max(1, Math.round(valorBase * (float) escala));
    }
}
