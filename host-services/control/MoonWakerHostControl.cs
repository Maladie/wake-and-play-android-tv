using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace MoonWaker.HostControl
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new ControlForm());
        }
    }

    internal sealed class ControlForm : Form
    {
        private readonly Color background = Color.FromArgb(17, 20, 28);
        private readonly Color panel = Color.FromArgb(28, 33, 45);
        private readonly Color accent = Color.FromArgb(116, 100, 255);
        private readonly Color muted = Color.FromArgb(164, 171, 193);
        private readonly Label gatewayState = new Label();
        private readonly Label gatewayDetails = new Label();
        private readonly Label activeProfile = new Label();
        private readonly ListView profiles = new ListView();
        private readonly Label footer = new Label();
        private readonly Timer timer = new Timer();
        private readonly JavaScriptSerializer json = new JavaScriptSerializer();
        private readonly string script;
        private bool refreshing;

        public ControlForm()
        {
            script = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Invoke-MoonWakerHostControl.ps1");
            Text = "MoonWaker Host Control";
            ClientSize = new Size(1040, 690);
            MinimumSize = new Size(940, 640);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = background;
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 9.5F);

            Label title = MakeLabel("MOONWAKER", 24F, FontStyle.Bold, Color.White);
            title.SetBounds(34, 22, 520, 45);
            Controls.Add(title);
            Label subtitle = MakeLabel("HOST CONTROL", 9F, FontStyle.Bold, muted);
            subtitle.SetBounds(38, 61, 260, 24);
            Controls.Add(subtitle);

            Panel gatewayPanel = NewPanel(34, 96, 972, 154);
            Controls.Add(gatewayPanel);
            Label gatewayTitle = MakeLabel("Gateway komputera", 16F, FontStyle.Bold, Color.White);
            gatewayTitle.SetBounds(24, 18, 300, 30);
            gatewayPanel.Controls.Add(gatewayTitle);
            gatewayState.SetBounds(26, 54, 280, 26);
            gatewayState.Font = new Font("Segoe UI", 11F, FontStyle.Bold);
            gatewayPanel.Controls.Add(gatewayState);
            gatewayDetails.SetBounds(26, 82, 360, 45);
            gatewayDetails.ForeColor = muted;
            gatewayPanel.Controls.Add(gatewayDetails);
            AddActionButton(gatewayPanel, "Uruchom", 430, 32, delegate { RunAction("StartGateway", null); });
            AddActionButton(gatewayPanel, "Zatrzymaj", 555, 32, delegate { RunAction("StopGateway", null); });
            AddActionButton(gatewayPanel, "Restart", 680, 32, delegate { RunAction("RestartGateway", null); });
            Button pair = AddActionButton(gatewayPanel, "Sparuj TV", 805, 32, delegate { PairGateway(); });
            pair.BackColor = accent;
            pair.FlatAppearance.BorderSize = 0;
            activeProfile.SetBounds(430, 94, 500, 30);
            activeProfile.ForeColor = muted;
            gatewayPanel.Controls.Add(activeProfile);

            Panel profilePanel = NewPanel(34, 266, 972, 346);
            Controls.Add(profilePanel);
            Label profileTitle = MakeLabel("Profile i Bridge'e", 16F, FontStyle.Bold, Color.White);
            profileTitle.SetBounds(24, 16, 400, 32);
            profilePanel.Controls.Add(profileTitle);
            Label profileHint = MakeLabel("Jeden nadzorca Bridge na każde konto Windows", 9F, FontStyle.Regular, muted);
            profileHint.SetBounds(26, 48, 500, 24);
            profilePanel.Controls.Add(profileHint);

            profiles.SetBounds(24, 82, 924, 180);
            profiles.View = View.Details;
            profiles.FullRowSelect = true;
            profiles.HideSelection = false;
            profiles.MultiSelect = false;
            profiles.BackColor = Color.FromArgb(20, 24, 33);
            profiles.ForeColor = Color.White;
            profiles.BorderStyle = BorderStyle.None;
            profiles.Columns.Add("Profil", 175);
            profiles.Columns.Add("Użytkownik", 165);
            profiles.Columns.Add("Nadzorca", 120);
            profiles.Columns.Add("Discord", 105);
            profiles.Columns.Add("Vibepollo", 105);
            profiles.Columns.Add("Playnite", 105);
            profiles.Columns.Add("Używany", 110);
            profilePanel.Controls.Add(profiles);

            AddActionButton(profilePanel, "Uruchom Bridge", 24, 282, delegate { RunProfileAction("StartProfile"); });
            AddActionButton(profilePanel, "Zatrzymaj", 174, 282, delegate { RunProfileAction("StopProfile"); });
            AddActionButton(profilePanel, "Restart", 299, 282, delegate { RunProfileAction("RestartProfile"); });
            AddActionButton(profilePanel, "Usuń dane Discorda", 424, 282, delegate { ClearDiscord(); }, 180);
            Button remove = AddActionButton(profilePanel, "Usuń profil", 619, 282, delegate { RemoveProfile(); }, 140);
            remove.ForeColor = Color.FromArgb(255, 180, 180);
            AddActionButton(profilePanel, "Odśwież", 774, 282, delegate { RefreshStatus(); }, 140);

            footer.SetBounds(36, 628, 968, 36);
            footer.ForeColor = muted;
            Controls.Add(footer);

            timer.Interval = 5000;
            timer.Tick += delegate { RefreshStatus(); };
            Shown += delegate { RefreshStatus(); timer.Start(); };
        }

        private Panel NewPanel(int left, int top, int width, int height)
        {
            Panel value = new Panel();
            value.SetBounds(left, top, width, height);
            value.BackColor = panel;
            return value;
        }

        private Label MakeLabel(string text, float size, FontStyle style, Color color)
        {
            Label value = new Label();
            value.Text = text;
            value.Font = new Font("Segoe UI", size, style);
            value.ForeColor = color;
            value.BackColor = Color.Transparent;
            return value;
        }

        private Button AddActionButton(Control parent, string text, int left, int top, EventHandler handler, int width)
        {
            Button button = new Button();
            button.Text = text;
            button.SetBounds(left, top, width, 38);
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderColor = Color.FromArgb(77, 85, 109);
            button.BackColor = panel;
            button.ForeColor = Color.White;
            button.Cursor = Cursors.Hand;
            button.Click += handler;
            parent.Controls.Add(button);
            return button;
        }

        private Button AddActionButton(Control parent, string text, int left, int top, EventHandler handler)
        {
            return AddActionButton(parent, text, left, top, handler, 112);
        }

        private async void RefreshStatus()
        {
            if (refreshing) return;
            refreshing = true;
            footer.Text = "Sprawdzam usługi…";
            try
            {
                Dictionary<string, object> result = await RunControlAsync("Status", null, false);
                if (!IsOk(result)) throw new InvalidOperationException(GetText(result, "error", "Nie udało się odczytać statusu."));
                RenderStatus(result);
                footer.Text = "Status odświeżony: " + DateTime.Now.ToString("HH:mm:ss") + ". Operacje dotyczące innych kont mogą wymagać zalogowania na ten profil.";
            }
            catch (Exception ex)
            {
                gatewayState.Text = "Status niedostępny";
                gatewayState.ForeColor = Color.FromArgb(255, 170, 170);
                footer.Text = ex.Message;
            }
            finally { refreshing = false; }
        }

        private void RenderStatus(Dictionary<string, object> result)
        {
            Dictionary<string, object> gateway = AsDictionary(result["gateway"]);
            bool running = GetBool(gateway, "running");
            gatewayState.Text = running ? "● ONLINE" : "● ZATRZYMANY";
            gatewayState.ForeColor = running ? Color.FromArgb(129, 226, 169) : Color.FromArgb(255, 170, 170);
            gatewayDetails.Text = "Port " + GetText(gateway, "port", "—") + "  •  sparowane urządzenia: " +
                GetText(gateway, "paired_clients", "0") + (GetBool(gateway, "pairing") ? "\nParowanie aktywne" : "\nParowanie nieaktywne");
            string active = GetText(result, "active_profile", "");
            activeProfile.Text = String.IsNullOrWhiteSpace(active) ? "Ostatnio używany profil: brak danych" : "Ostatnio używany profil: " + active;

            string selected = SelectedProfileId();
            profiles.BeginUpdate();
            profiles.Items.Clear();
            IEnumerable values = result["profiles"] as IEnumerable;
            if (values != null)
            {
                foreach (object item in values)
                {
                    Dictionary<string, object> profile = AsDictionary(item);
                    string id = GetText(profile, "id", "");
                    ListViewItem row = new ListViewItem(GetText(profile, "name", id));
                    row.Name = id;
                    row.Tag = profile;
                    row.SubItems.Add(GetText(profile, "owner", "—"));
                    row.SubItems.Add(StatusLabel(GetText(profile, "supervisor", "stopped")));
                    row.SubItems.Add(StatusLabel(GetText(profile, "discord", "disabled")));
                    row.SubItems.Add(StatusLabel(GetText(profile, "vibepollo", "disabled")));
                    row.SubItems.Add(StatusLabel(GetText(profile, "playnite", "disabled")));
                    row.SubItems.Add(GetBool(profile, "last_used") ? "AKTYWNY" : "");
                    profiles.Items.Add(row);
                    if (id == selected) row.Selected = true;
                }
            }
            profiles.EndUpdate();
        }

        private static string StatusLabel(string value)
        {
            if (value == "running" || value == "online") return "ONLINE";
            if (value == "disabled") return "WYŁ.";
            if (value == "unavailable") return "INNE KONTO";
            return "OFFLINE";
        }

        private string SelectedProfileId()
        {
            if (profiles.SelectedItems.Count == 0) return null;
            return profiles.SelectedItems[0].Name;
        }

        private void RunProfileAction(string action)
        {
            string id = SelectedProfileId();
            if (String.IsNullOrWhiteSpace(id)) { MessageBox.Show(this, "Wybierz profil.", "MoonWaker", MessageBoxButtons.OK, MessageBoxIcon.Information); return; }
            RunAction(action, id);
        }

        private async void RunAction(string action, string profile)
        {
            try
            {
                footer.Text = "Wykonuję operację…";
                Dictionary<string, object> result = await RunControlAsync(action, profile, false);
                if (!IsOk(result))
                {
                    DialogResult elevate = MessageBox.Show(this, GetText(result, "error", "Operacja nie powiodła się.") +
                        "\n\nSpróbować z uprawnieniami administratora?", "MoonWaker Host Control",
                        MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
                    if (elevate == DialogResult.Yes) result = await RunControlAsync(action, profile, true);
                }
                if (!IsOk(result)) throw new InvalidOperationException(GetText(result, "error", "Operacja nie powiodła się."));
                await Task.Delay(700);
                RefreshStatus();
            }
            catch (Exception ex) { MessageBox.Show(this, ex.Message, "MoonWaker Host Control", MessageBoxButtons.OK, MessageBoxIcon.Error); RefreshStatus(); }
        }

        private async void PairGateway()
        {
            try
            {
                Dictionary<string, object> result = await RunControlAsync("PairGateway", null, false);
                if (!IsOk(result)) result = await RunControlAsync("PairGateway", null, true);
                if (!IsOk(result)) throw new InvalidOperationException(GetText(result, "error", "Nie udało się uruchomić parowania."));
                string code = GetText(result, "pairing_code", "");
                MessageBox.Show(this, "Kod parowania:\n\n" + code + "\n\nKod jest ważny przez 10 minut.",
                    "Parowanie MoonWaker", MessageBoxButtons.OK, MessageBoxIcon.Information);
                RefreshStatus();
            }
            catch (Exception ex) { MessageBox.Show(this, ex.Message, "MoonWaker Host Control", MessageBoxButtons.OK, MessageBoxIcon.Error); }
        }

        private void ClearDiscord()
        {
            string id = SelectedProfileId();
            if (String.IsNullOrWhiteSpace(id)) { MessageBox.Show(this, "Wybierz profil.", "MoonWaker", MessageBoxButtons.OK, MessageBoxIcon.Information); return; }
            DialogResult result = MessageBox.Show(this, "Usunąć token OAuth i lokalne dane Discorda dla profilu " + id + "?",
                "Usuń dane Discorda", MessageBoxButtons.YesNo, MessageBoxIcon.Warning);
            if (result != DialogResult.Yes) return;
            DialogResult machine = MessageBox.Show(this,
                "Czy usunąć również wspólne dla komputera Client ID i Client Secret aplikacji Discord?\n\nWybierz Nie, jeśli inne profile nadal korzystają z Discorda.",
                "Wspólne dane aplikacji Discord", MessageBoxButtons.YesNoCancel, MessageBoxIcon.Warning);
            if (machine == DialogResult.Cancel) return;
            RunAction(machine == DialogResult.Yes ? "ClearDiscordMachine" : "ClearDiscord", id);
        }

        private void RemoveProfile()
        {
            string id = SelectedProfileId();
            if (String.IsNullOrWhiteSpace(id)) { MessageBox.Show(this, "Wybierz profil.", "MoonWaker", MessageBoxButtons.OK, MessageBoxIcon.Information); return; }
            if (MessageBox.Show(this, "Usunąć profil " + id + "?\n\nBridge zostanie zatrzymany, autostart wyrejestrowany, a tokeny i konfiguracja profilu trwale usunięte.",
                "Usuń profil MoonWaker", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.Yes) RunAction("RemoveProfile", id);
        }

        private async Task<Dictionary<string, object>> RunControlAsync(string action, string profile, bool elevated)
        {
            return await Task.Run(delegate
            {
                if (!File.Exists(script)) throw new FileNotFoundException("Brak komponentu sterującego.", script);
                string resultPath = Path.Combine(Path.GetTempPath(), "MoonWakerControl-" + Guid.NewGuid().ToString("N") + ".json");
                string arguments = "-NoProfile -ExecutionPolicy Bypass -File " + Quote(script) + " -Action " + Quote(action) +
                    (String.IsNullOrWhiteSpace(profile) ? "" : " -ProfileId " + Quote(profile)) + " -ResultPath " + Quote(resultPath);
                try
                {
                    ProcessStartInfo info = new ProcessStartInfo("powershell.exe", arguments);
                    info.UseShellExecute = elevated;
                    if (elevated) info.Verb = "runas";
                    else
                    {
                        info.CreateNoWindow = true;
                        info.RedirectStandardOutput = true;
                        info.RedirectStandardError = true;
                        info.StandardOutputEncoding = Encoding.Default;
                        info.StandardErrorEncoding = Encoding.Default;
                    }
                    using (Process process = Process.Start(info))
                    {
                        string output = elevated ? "" : process.StandardOutput.ReadToEnd();
                        process.WaitForExit();
                        if (File.Exists(resultPath)) output = File.ReadAllText(resultPath, Encoding.UTF8).TrimStart('\uFEFF');
                        if (String.IsNullOrWhiteSpace(output)) return Error("Brak odpowiedzi komponentu sterującego.");
                        return json.Deserialize<Dictionary<string, object>>(output.Trim());
                    }
                }
                catch (System.ComponentModel.Win32Exception ex) { return Error(ex.NativeErrorCode == 1223 ? "Anulowano prośbę o uprawnienia administratora." : ex.Message); }
                finally { try { File.Delete(resultPath); } catch { } }
            });
        }

        private static Dictionary<string, object> Error(string message)
        {
            Dictionary<string, object> value = new Dictionary<string, object>();
            value["ok"] = false;
            value["error"] = message;
            return value;
        }

        private static Dictionary<string, object> AsDictionary(object value)
        {
            return value as Dictionary<string, object> ?? new Dictionary<string, object>();
        }

        private static bool IsOk(Dictionary<string, object> value) { return GetBool(value, "ok"); }
        private static bool GetBool(Dictionary<string, object> value, string key)
        {
            object found;
            if (!value.TryGetValue(key, out found) || found == null) return false;
            bool parsed;
            return found is bool ? (bool)found : Boolean.TryParse(found.ToString(), out parsed) && parsed;
        }

        private static string GetText(Dictionary<string, object> value, string key, string fallback)
        {
            object found;
            return value.TryGetValue(key, out found) && found != null ? found.ToString() : fallback;
        }

        private static string Quote(string value) { return "\"" + value.Replace("\"", "\\\"") + "\""; }
    }
}
